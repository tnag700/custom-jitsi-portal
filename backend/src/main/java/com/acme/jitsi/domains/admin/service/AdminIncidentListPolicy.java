package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

final class AdminIncidentListPolicy {

  private static final String ACTIVE_VIEW = "active";
  private static final String CRITICAL_VIEW = "critical";
  private static final String NEW_SPIKES_VIEW = "new-spikes";
  private static final String REFINEMENT_VIEW = "needs-refinement";
  private static final String SEVERITY_CRITICAL = CRITICAL_VIEW;
  private static final String SEVERITY_WARN = "warn";
  private static final String SEVERITY_INFO = "info";
  private static final String FACET_CRITICAL = "severity:critical";
  private static final String FACET_WARN = "severity:warn";
  private static final String FACET_SCOPE_ROOM = "scope:room";
  private static final String FACET_MEETING = "scope:meeting";
  private static final String FACET_TOKEN = "category:token";
  private static final String FACET_CONFIG = "category:config";
  private static final String CATEGORY_TOKEN = "TOKEN";
  private static final String CATEGORY_CONFIG = "CONFIG";

  private AdminIncidentListPolicy() {
  }

  static AdminIncidentListResponse list(
      AdminIncidentsReadModel readModel,
      String tenantId,
      AdminIncidentsService.AdminIncidentListQuery query,
      Clock clock) {
    ConfigSetEnvironmentType environmentType =
        IncidentEnvironmentPolicy.resolveEnvironment(query.environment());
    AdminDashboardPeriod period = AdminDashboardPeriod.fromToken(query.period());
    int pageSize = AdminIncidentAggregationPolicy.clampPageSize(query.limit());
    int offset = Math.max(query.offset(), 0);
    List<AdminIncidentsReadModel.IncidentSignal> signals = loadSignals(
        readModel,
        tenantId,
        environmentType,
        query,
        period,
        clock,
        pageSize,
        offset);
    List<AdminIncidentAggregate> incidents =
        AdminIncidentAggregationPolicy.toAggregates(signals);
    return resolveResponse(
        incidents,
        tenantId,
        query,
        period,
        environmentType,
        pageSize,
        offset,
        clock);
  }

  private static List<AdminIncidentsReadModel.IncidentSignal> loadSignals(
      AdminIncidentsReadModel readModel,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      AdminIncidentsService.AdminIncidentListQuery query,
      AdminDashboardPeriod period,
      Clock clock,
      int pageSize,
      int offset) {
    return readModel.loadSignals(new AdminIncidentsReadModel.SignalFilter(
        tenantId,
        environmentType,
        period.from(clock),
        Instant.now(clock),
        IncidentNormalizationPolicy.blankToNull(query.roomId()),
        IncidentNormalizationPolicy.blankToNull(query.meetingId()),
        IncidentNormalizationPolicy.blankToNull(query.subjectId()),
        IncidentNormalizationPolicy.blankToNull(query.errorCode()),
        IncidentNormalizationPolicy.blankToNull(query.category()),
        AdminIncidentAggregationPolicy.rawSampleLimit(pageSize, offset)));
  }

  private static AdminIncidentListResponse resolveResponse(
      List<AdminIncidentAggregate> incidents,
      String tenantId,
      AdminIncidentsService.AdminIncidentListQuery query,
      AdminDashboardPeriod period,
      ConfigSetEnvironmentType environmentType,
      int pageSize,
      int offset,
      Clock clock) {
    String selectedView = resolveSavedView(query.savedView(), query);
    List<AdminIncidentAggregate> viewScoped = incidents.stream()
        .filter(incident -> matchesSavedView(incident, selectedView, clock))
        .toList();
    String quickFacetToken = resolveQuickFacet(query.quickFacet(), query);
    List<AdminIncidentListResponse.QuickFacet> quickFacets =
        buildQuickFacets(viewScoped, quickFacetToken);
    List<AdminIncidentAggregate> filtered =
        filterIncidents(viewScoped, quickFacetToken, query);
    List<AdminIncidentListResponse.IncidentListItem> items =
        pageItems(filtered, offset, pageSize, tenantId, clock);

    return new AdminIncidentListResponse(
        period.token(),
        IncidentEnvironmentPolicy.environmentLabel(environmentType),
        tenantId,
        Instant.now(clock).toString(),
        selectedView,
        quickFacetToken,
        buildSavedViews(),
        quickFacets,
        buildQueueSort(query.sortBy(), query.direction()),
        pageSize,
        offset,
        filtered.size(),
        items);
  }

  private static List<AdminIncidentAggregate> filterIncidents(
      List<AdminIncidentAggregate> viewScoped,
      String quickFacetToken,
      AdminIncidentsService.AdminIncidentListQuery query) {
    return viewScoped.stream()
        .filter(incident -> matchesQuickFacet(incident, quickFacetToken))
        .filter(incident -> matchesSeverity(incident, query.severity()))
        .sorted(resolveSort(query.sortBy(), query.direction()))
        .toList();
  }

  private static List<AdminIncidentListResponse.IncidentListItem> pageItems(
      List<AdminIncidentAggregate> filtered,
      int offset,
      int pageSize,
      String tenantId,
      Clock clock) {
    return filtered.stream()
        .skip(offset)
        .limit(pageSize)
        .map(incident -> toListItem(incident, tenantId, clock))
        .toList();
  }

  private static AdminIncidentListResponse.IncidentListItem toListItem(
      AdminIncidentAggregate incident,
      String tenantId,
      Clock clock) {
    return new AdminIncidentListResponse.IncidentListItem(
        incident.incidentId(),
        incident.lastOccurredAt().toString(),
        incident.errorCode(),
        incident.category(),
        tenantId,
        incident.roomId(),
        incident.meetingId(),
        incident.affectedSubjects().size(),
        incident.severity(),
        AdminIncidentViewSupport.affectedEntitySummary(incident),
        AdminIncidentViewSupport.freshnessHint(incident, clock));
  }

  private static List<AdminIncidentListResponse.SavedView> buildSavedViews() {
    return List.of(
        new AdminIncidentListResponse.SavedView(
            ACTIVE_VIEW,
            "Active",
            "Текущая triage queue для свежих или незакрытых сигналов."),
        new AdminIncidentListResponse.SavedView(
            CRITICAL_VIEW,
            "Critical",
            "Критические кейсы и блокирующие отказы выше остальных."),
        new AdminIncidentListResponse.SavedView(
            NEW_SPIKES_VIEW,
            "New spikes",
            "Свежие всплески по нескольким субъектам или повторяющимся сигналам."),
        new AdminIncidentListResponse.SavedView(
            REFINEMENT_VIEW,
            "Needs refinement",
            "Сигналы с неполным affected scope, где нужен дополнительный drill-down."));
  }

  private static List<AdminIncidentListResponse.QuickFacet> buildQuickFacets(
      List<AdminIncidentAggregate> incidents,
      String quickFacetToken) {
    return List.of(
        quickFacet(
            incidents,
            quickFacetToken,
            FACET_CRITICAL,
            "Critical",
            incident -> SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity())),
        quickFacet(
            incidents,
            quickFacetToken,
            FACET_WARN,
            "Warn",
            incident -> SEVERITY_WARN.equalsIgnoreCase(incident.severity())),
        quickFacet(
            incidents,
            quickFacetToken,
            FACET_SCOPE_ROOM,
            "Комнаты",
            incident -> incident.roomId() != null),
        quickFacet(
            incidents,
            quickFacetToken,
            FACET_MEETING,
            "Встречи",
            incident -> incident.meetingId() != null),
        quickFacet(
            incidents,
            quickFacetToken,
            FACET_TOKEN,
            "Token",
            incident -> CATEGORY_TOKEN.equalsIgnoreCase(incident.category())),
        quickFacet(
            incidents,
            quickFacetToken,
            FACET_CONFIG,
            "Config",
            incident -> CATEGORY_CONFIG.equalsIgnoreCase(incident.category())));
  }

  private static AdminIncidentListResponse.QuickFacet quickFacet(
      Collection<AdminIncidentAggregate> incidents,
      String quickFacetToken,
      String token,
      String label,
      Predicate<AdminIncidentAggregate> predicate) {
    long count = incidents.stream().filter(predicate).count();
    return new AdminIncidentListResponse.QuickFacet(
        token,
        label,
        count,
        token.equalsIgnoreCase(IncidentNormalizationPolicy.blankToNull(quickFacetToken)));
  }

  private static AdminIncidentListResponse.QueueSort buildQueueSort(
      String sortBy,
      String direction) {
    String token = normalizeSort(sortBy);
    String resolvedDirection = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
    String label = switch (token) {
      case "time" -> "Freshness";
      case "severity", "queue" -> "Severity + freshness";
      default -> "Severity + freshness";
    };
    return new AdminIncidentListResponse.QueueSort(token, label, resolvedDirection);
  }

  private static boolean matchesSavedView(
      AdminIncidentAggregate incident,
      String savedView,
      Clock clock) {
    return switch (savedView) {
      case CRITICAL_VIEW -> SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity());
      case NEW_SPIKES_VIEW ->
          incident.signals().size() >= 3 || incident.affectedSubjects().size() >= 3;
      case REFINEMENT_VIEW -> incident.roomId() == null || incident.meetingId() == null;
      case ACTIVE_VIEW -> isActiveIncident(incident, clock);
      default -> true;
    };
  }

  private static boolean isActiveIncident(AdminIncidentAggregate incident, Clock clock) {
    Duration age = Duration.between(incident.lastOccurredAt(), Instant.now(clock));
    return !age.isNegative()
        && (age.compareTo(Duration.ofHours(6)) <= 0
            || !SEVERITY_INFO.equalsIgnoreCase(incident.severity()));
  }

  private static String resolveSavedView(
      String requestedView,
      AdminIncidentsService.AdminIncidentListQuery query) {
    if (IncidentNormalizationPolicy.hasText(requestedView)
        && isSupportedSavedView(requestedView)) {
      return requestedView.trim().toLowerCase(Locale.ROOT);
    }
    if (SEVERITY_CRITICAL.equalsIgnoreCase(
        IncidentNormalizationPolicy.blankToNull(query.severity()))) {
      return CRITICAL_VIEW;
    }
    return ACTIVE_VIEW;
  }

  private static boolean isSupportedSavedView(String token) {
    return switch (token.trim().toLowerCase(Locale.ROOT)) {
      case ACTIVE_VIEW, CRITICAL_VIEW, NEW_SPIKES_VIEW, REFINEMENT_VIEW -> true;
      default -> false;
    };
  }

  private static String resolveQuickFacet(
      String requestedFacet,
      AdminIncidentsService.AdminIncidentListQuery query) {
    if (IncidentNormalizationPolicy.hasText(requestedFacet)
        && isSupportedQuickFacet(requestedFacet)) {
      return requestedFacet.trim().toLowerCase(Locale.ROOT);
    }
    if (IncidentNormalizationPolicy.hasText(query.meetingId())) {
      return FACET_MEETING;
    }
    if (IncidentNormalizationPolicy.hasText(query.roomId())) {
      return FACET_SCOPE_ROOM;
    }
    if (SEVERITY_CRITICAL.equalsIgnoreCase(
        IncidentNormalizationPolicy.blankToNull(query.severity()))) {
      return FACET_CRITICAL;
    }
    String categoryKey =
        IncidentNormalizationPolicy.normalizeCategory(query.category(), null);
    if (CATEGORY_TOKEN.equals(categoryKey)) {
      return FACET_TOKEN;
    }
    if (CATEGORY_CONFIG.equals(categoryKey)) {
      return FACET_CONFIG;
    }
    return null;
  }

  private static boolean isSupportedQuickFacet(String token) {
    return switch (token.trim().toLowerCase(Locale.ROOT)) {
      case FACET_CRITICAL,
          FACET_WARN,
          FACET_SCOPE_ROOM,
          FACET_MEETING,
          FACET_TOKEN,
          FACET_CONFIG -> true;
      default -> false;
    };
  }

  private static boolean matchesQuickFacet(
      AdminIncidentAggregate incident,
      String quickFacetToken) {
    if (!IncidentNormalizationPolicy.hasText(quickFacetToken)) {
      return true;
    }
    return switch (quickFacetToken.trim().toLowerCase(Locale.ROOT)) {
      case FACET_CRITICAL ->
          SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity());
      case FACET_WARN -> SEVERITY_WARN.equalsIgnoreCase(incident.severity());
      case FACET_SCOPE_ROOM -> incident.roomId() != null;
      case FACET_MEETING -> incident.meetingId() != null;
      case FACET_TOKEN -> CATEGORY_TOKEN.equalsIgnoreCase(incident.category());
      case FACET_CONFIG -> CATEGORY_CONFIG.equalsIgnoreCase(incident.category());
      default -> true;
    };
  }

  private static Comparator<AdminIncidentAggregate> resolveSort(
      String sortBy,
      String direction) {
    Comparator<AdminIncidentAggregate> comparator = switch (normalizeSort(sortBy)) {
      case "time" ->
          Comparator.comparing(
              AdminIncidentAggregate::lastOccurredAt,
              Comparator.reverseOrder());
      case "severity", "queue" ->
          Comparator.comparingInt(AdminIncidentListPolicy::severityRank)
              .thenComparing(
                  AdminIncidentAggregate::lastOccurredAt,
                  Comparator.reverseOrder());
      default ->
          Comparator.comparingInt(AdminIncidentListPolicy::severityRank)
              .thenComparing(
                  AdminIncidentAggregate::lastOccurredAt,
                  Comparator.reverseOrder());
    };
    return "asc".equalsIgnoreCase(direction) ? comparator.reversed() : comparator;
  }

  private static String normalizeSort(String sortBy) {
    return IncidentNormalizationPolicy.hasText(sortBy)
        ? sortBy.trim().toLowerCase(Locale.ROOT)
        : "queue";
  }

  private static int severityRank(AdminIncidentAggregate incident) {
    return switch (incident.severity()) {
      case SEVERITY_CRITICAL -> 0;
      case SEVERITY_WARN -> 1;
      default -> 2;
    };
  }

  private static boolean matchesSeverity(
      AdminIncidentAggregate incident,
      String requestedSeverity) {
    return requestedSeverity == null
        || requestedSeverity.isBlank()
        || incident.severity().equalsIgnoreCase(requestedSeverity.trim());
  }
}
