package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentSearchResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

final class AdminIncidentSearchPolicy {

  private static final String EXACT_MATCH = "exact-match";
  private static final String CANDIDATE_LIST = "candidate-list";
  private static final String NOT_FOUND = "not-found";
  private static final String NO_MATCH_MESSAGE =
      "Совпадений не найдено. Уточните время, tenant или entity filters.";
  private static final String REFINE_FILTERS_MESSAGE =
      "Уточните tenant или entity filters.";

  private AdminIncidentSearchPolicy() {
  }

  static AdminIncidentSearchResponse search(
      AdminIncidentsReadModel readModel,
      String tenantId,
      AdminIncidentsService.AdminIncidentSearchQuery query,
      Clock clock,
      Duration retentionWindow) {
    ConfigSetEnvironmentType environmentType =
        IncidentEnvironmentPolicy.resolveEnvironment(query.environment());
    Instant searchFrom = resolveSearchFrom(query, clock, retentionWindow);
    Instant searchTo = resolveSearchTo(query, clock);
    List<AdminIncidentsReadModel.IncidentSignal> signals = readModel.loadSignals(
        toSignalFilter(
            tenantId,
            environmentType,
            searchFrom,
            searchTo,
            query));
    List<AdminIncidentAggregate> incidents =
        AdminIncidentAggregationPolicy.toAggregates(signals);
    return resolveResponse(incidents, searchFrom, searchTo, query, clock);
  }

  private static AdminIncidentSearchResponse resolveResponse(
      List<AdminIncidentAggregate> incidents,
      Instant searchFrom,
      Instant searchTo,
      AdminIncidentsService.AdminIncidentSearchQuery query,
      Clock clock) {
    String correlationId =
        IncidentNormalizationPolicy.firstNonBlank(query.traceId(), query.requestId());
    if (correlationId != null) {
      return resolveCorrelationResponse(incidents, correlationId);
    }

    Instant targetInstant =
        resolveSearchTarget(searchFrom, searchTo, query, clock);
    List<AdminIncidentSearchResponse.SearchCandidate> candidates = incidents.stream()
        .sorted(candidateComparator(query, targetInstant))
        .map(AdminIncidentSearchPolicy::toSearchCandidate)
        .toList();
    return resolveCandidateResponse(candidates);
  }

  private static AdminIncidentSearchResponse resolveCorrelationResponse(
      List<AdminIncidentAggregate> incidents,
      String correlationId) {
    return incidents.stream()
        .filter(incident -> incident.signals().stream()
            .anyMatch(signal -> correlationId.equals(signal.traceId())
                || correlationId.equals(signal.requestId())))
        .findFirst()
        .map(incident -> exactMatch(incident.incidentId()))
        .orElseGet(AdminIncidentSearchPolicy::notFound);
  }

  private static AdminIncidentSearchResponse resolveCandidateResponse(
      List<AdminIncidentSearchResponse.SearchCandidate> candidates) {
    if (candidates.isEmpty()) {
      return notFound();
    }
    if (candidates.size() == 1) {
      return exactMatch(candidates.get(0).incidentId());
    }
    return new AdminIncidentSearchResponse(
        CANDIDATE_LIST,
        null,
        null,
        REFINE_FILTERS_MESSAGE,
        candidates);
  }

  private static AdminIncidentSearchResponse exactMatch(String incidentId) {
    return new AdminIncidentSearchResponse(
        EXACT_MATCH,
        incidentId,
        null,
        null,
        List.of());
  }

  private static AdminIncidentSearchResponse notFound() {
    return new AdminIncidentSearchResponse(
        NOT_FOUND,
        null,
        null,
        NO_MATCH_MESSAGE,
        List.of());
  }

  private static AdminIncidentsReadModel.SignalFilter toSignalFilter(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      Instant searchFrom,
      Instant searchTo,
      AdminIncidentsService.AdminIncidentSearchQuery query) {
    return new AdminIncidentsReadModel.SignalFilter(
        tenantId,
        environmentType,
        searchFrom,
        searchTo,
        null,
        IncidentNormalizationPolicy.blankToNull(query.meetingId()),
        null,
        IncidentNormalizationPolicy.blankToNull(query.errorCode()),
        null,
        AdminIncidentAggregationPolicy.rawSampleLimit(
            AdminIncidentAggregationPolicy.DEFAULT_PAGE_SIZE,
            0));
  }

  private static Instant resolveSearchFrom(
      AdminIncidentsService.AdminIncidentSearchQuery query,
      Clock clock,
      Duration retentionWindow) {
    if (IncidentNormalizationPolicy.hasText(query.from())) {
      return parseSearchInstant(query.from().trim(), "from");
    }
    return Instant.now(clock).minus(retentionWindow);
  }

  private static Instant resolveSearchTo(
      AdminIncidentsService.AdminIncidentSearchQuery query,
      Clock clock) {
    if (IncidentNormalizationPolicy.hasText(query.to())) {
      return parseSearchInstant(query.to().trim(), "to");
    }
    return Instant.now(clock);
  }

  private static Instant resolveSearchTarget(
      Instant from,
      Instant to,
      AdminIncidentsService.AdminIncidentSearchQuery query,
      Clock clock) {
    Instant explicitFrom =
        IncidentNormalizationPolicy.hasText(query.from()) ? from : null;
    Instant explicitTo =
        IncidentNormalizationPolicy.hasText(query.to()) ? to : null;
    if (explicitFrom != null && explicitTo != null) {
      return explicitFrom.plusMillis(
          Duration.between(explicitFrom, explicitTo).toMillis() / 2L);
    }
    if (explicitFrom != null) {
      return explicitFrom;
    }
    if (explicitTo != null) {
      return explicitTo;
    }
    return Instant.now(clock);
  }

  private static Instant parseSearchInstant(
      String value,
      String parameterName) {
    try {
      return Instant.parse(value);
    } catch (DateTimeParseException ex) {
      throw new AdminIncidentsInvalidRequestException(
          "Параметр %s должен быть ISO-8601 timestamp.".formatted(parameterName),
          ex);
    }
  }

  private static Comparator<AdminIncidentAggregate> candidateComparator(
      AdminIncidentsService.AdminIncidentSearchQuery query,
      Instant targetInstant) {
    return Comparator.comparingInt(
            (AdminIncidentAggregate incident) ->
                entityReferenceScore(incident, query))
        .reversed()
        .thenComparingLong(
            incident -> timeDistanceMillis(incident, targetInstant))
        .thenComparing(
            AdminIncidentAggregate::lastOccurredAt,
            Comparator.reverseOrder());
  }

  private static int entityReferenceScore(
      AdminIncidentAggregate incident,
      AdminIncidentsService.AdminIncidentSearchQuery query) {
    if (IncidentNormalizationPolicy.hasText(query.meetingId())
        && query.meetingId().trim().equalsIgnoreCase(
            IncidentNormalizationPolicy.blankToNull(incident.meetingId()))) {
      return 1;
    }
    return 0;
  }

  private static long timeDistanceMillis(
      AdminIncidentAggregate incident,
      Instant targetInstant) {
    long fromStart =
        Math.abs(Duration.between(incident.firstOccurredAt(), targetInstant).toMillis());
    long fromEnd =
        Math.abs(Duration.between(incident.lastOccurredAt(), targetInstant).toMillis());
    return Math.min(fromStart, fromEnd);
  }

  private static AdminIncidentSearchResponse.SearchCandidate toSearchCandidate(
      AdminIncidentAggregate incident) {
    return new AdminIncidentSearchResponse.SearchCandidate(
        incident.incidentId(),
        incident.lastOccurredAt().toString(),
        incident.errorCode(),
        incident.meetingId());
  }
}
