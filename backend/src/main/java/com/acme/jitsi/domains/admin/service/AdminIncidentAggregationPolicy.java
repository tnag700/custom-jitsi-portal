package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class AdminIncidentAggregationPolicy {

  static final int DEFAULT_PAGE_SIZE = 50;
  static final int MAX_PAGE_SIZE = 200;

  private static final int DETAIL_LIMIT = 5_000;
  private static final int SAMPLE_MULTIPLIER = 25;
  private static final String SEVERITY_CRITICAL = "critical";
  private static final String SEVERITY_WARN = "warn";
  private static final String SEVERITY_INFO = "info";

  private AdminIncidentAggregationPolicy() {
  }

  static AdminIncidentAggregate loadById(
      AdminIncidentsReadModel readModel,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      Clock clock,
      Duration retentionWindow,
      String incidentId) {
    Instant lookupTo = Instant.now(clock);
    List<AdminIncidentsReadModel.IncidentSignal> signals = readModel.loadSignals(
        new AdminIncidentsReadModel.SignalFilter(
            tenantId,
            environmentType,
            lookupTo.minus(retentionWindow),
            lookupTo,
            null,
            null,
            null,
            null,
            null,
            detailLookupSampleLimit()));
    return findById(signals, incidentId);
  }

  static AdminIncidentAggregate findById(
      List<AdminIncidentsReadModel.IncidentSignal> signals,
      String incidentId) {
    return toAggregates(signals).stream()
        .filter(incident -> incident.incidentId().equals(incidentId))
        .findFirst()
        .orElseThrow(() -> new AdminIncidentNotFoundException(
            "Инцидент '%s' не найден в retention window.".formatted(incidentId)));
  }

  static List<AdminIncidentAggregate> toAggregates(
      List<AdminIncidentsReadModel.IncidentSignal> signals) {
    return groupSignals(signals).stream()
        .map(AdminIncidentAggregationPolicy::toAggregate)
        .toList();
  }

  static int clampPageSize(int limit) {
    if (limit <= 0) {
      return DEFAULT_PAGE_SIZE;
    }
    return Math.min(limit, MAX_PAGE_SIZE);
  }

  static int rawSampleLimit(int limit, int offset) {
    return Math.max((offset + clampPageSize(limit)) * SAMPLE_MULTIPLIER, 500);
  }

  private static List<GroupedIncident> groupSignals(
      List<AdminIncidentsReadModel.IncidentSignal> signals) {
    Map<String, List<AdminIncidentsReadModel.IncidentSignal>> grouped =
        new LinkedHashMap<>();
    for (AdminIncidentsReadModel.IncidentSignal signal : signals) {
      String category =
          IncidentNormalizationPolicy.normalizeCategory(signal.category(), signal.errorCode());
      String errorCode = IncidentNormalizationPolicy.normalizeErrorCode(signal.errorCode());
      if (category == null || errorCode == null) {
        continue;
      }
      String key = incidentKey(
          signal.tenantId(),
          signal.environmentType(),
          errorCode,
          category,
          signal.roomId(),
          signal.meetingId(),
          bucketStart(signal.occurredAt()));
      grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(signal);
    }
    return grouped.entrySet().stream()
        .map(entry -> new GroupedIncident(entry.getKey(), entry.getValue()))
        .toList();
  }

  private static AdminIncidentAggregate toAggregate(GroupedIncident groupedIncident) {
    List<AdminIncidentsReadModel.IncidentSignal> signals = groupedIncident.signals();
    AdminIncidentsReadModel.IncidentSignal first = signals.get(0);
    Instant fallbackOccurredAt = first.occurredAt();
    Instant firstOccurredAt = signals.stream()
        .map(AdminIncidentsReadModel.IncidentSignal::occurredAt)
        .min(Comparator.naturalOrder())
        .orElse(fallbackOccurredAt);
    Instant lastOccurredAt = signals.stream()
        .map(AdminIncidentsReadModel.IncidentSignal::occurredAt)
        .max(Comparator.naturalOrder())
        .orElse(fallbackOccurredAt);
    Set<String> subjects = signals.stream()
        .map(AdminIncidentsReadModel.IncidentSignal::subjectId)
        .filter(Objects::nonNull)
        .collect(LinkedHashSet::new, Set::add, Set::addAll);
    String category =
        IncidentNormalizationPolicy.normalizeCategory(first.category(), first.errorCode());

    return new AdminIncidentAggregate(
        stableIncidentId(groupedIncident.key()),
        first.tenantId(),
        first.environmentType(),
        IncidentNormalizationPolicy.normalizeErrorCode(first.errorCode()),
        category,
        IncidentNormalizationPolicy.blankToNull(first.roomId()),
        IncidentNormalizationPolicy.blankToNull(first.meetingId()),
        firstOccurredAt,
        lastOccurredAt,
        List.copyOf(signals),
        subjects,
        deriveSeverity(signals, subjects.size()));
  }

  private static String deriveSeverity(
      List<AdminIncidentsReadModel.IncidentSignal> signals,
      int uniqueSubjects) {
    if (hasCriticalImpact(signals, uniqueSubjects)) {
      return SEVERITY_CRITICAL;
    }
    if (hasWarningImpact(signals, uniqueSubjects)) {
      return SEVERITY_WARN;
    }
    return SEVERITY_INFO;
  }

  private static boolean hasCriticalImpact(
      List<AdminIncidentsReadModel.IncidentSignal> signals,
      int uniqueSubjects) {
    boolean highVolume = uniqueSubjects >= 10 || signals.size() >= 20;
    boolean criticalAlert = signals.stream()
        .anyMatch(signal -> SEVERITY_CRITICAL.equalsIgnoreCase(signal.alertSeverity()));
    boolean blocked = signals.stream()
        .anyMatch(signal -> "blocked".equalsIgnoreCase(signal.joinReadinessStatus()));
    boolean configMismatch = signals.stream()
        .anyMatch(signal -> "CONFIG_INCOMPATIBLE".equalsIgnoreCase(signal.errorCode()));
    boolean prodOrTest = signals.stream()
        .anyMatch(signal -> signal.environmentType() == ConfigSetEnvironmentType.PROD
            || signal.environmentType() == ConfigSetEnvironmentType.TEST);
    return criticalAlert || blocked || configMismatch || (prodOrTest && highVolume);
  }

  private static boolean hasWarningImpact(
      List<AdminIncidentsReadModel.IncidentSignal> signals,
      int uniqueSubjects) {
    boolean warningAlert = signals.stream()
        .anyMatch(signal -> "warning".equalsIgnoreCase(signal.alertSeverity()));
    return warningAlert || uniqueSubjects >= 3 || signals.size() >= 3;
  }

  private static int detailLookupSampleLimit() {
    return Math.max(rawSampleLimit(MAX_PAGE_SIZE, 0), DETAIL_LIMIT);
  }

  private static Instant bucketStart(Instant occurredAt) {
    long epochSeconds = occurredAt.truncatedTo(ChronoUnit.MINUTES).getEpochSecond();
    long bucket = epochSeconds - (epochSeconds % Duration.ofMinutes(15).getSeconds());
    return Instant.ofEpochSecond(bucket);
  }

  private static String incidentKey(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      String errorCode,
      String category,
      String roomId,
      String meetingId,
      Instant bucketStart) {
    return String.join(
        "|",
        IncidentNormalizationPolicy.normalizeForKey(tenantId),
        environmentType.name(),
        errorCode,
        category,
        IncidentNormalizationPolicy.normalizeForKey(roomId),
        IncidentNormalizationPolicy.normalizeForKey(meetingId),
        bucketStart.toString());
  }

  private static String stableIncidentId(String key) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(hash)
          .substring(0, 22);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private record GroupedIncident(
      String key,
      List<AdminIncidentsReadModel.IncidentSignal> signals) {
  }
}
