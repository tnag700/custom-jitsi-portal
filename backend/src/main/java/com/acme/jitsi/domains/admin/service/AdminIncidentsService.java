package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentSearchResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AdminIncidentsService {

  private static final int DEFAULT_PAGE_SIZE = 50;
  private static final int MAX_PAGE_SIZE = 200;
  private static final int DETAIL_LIMIT = 5_000;
  private static final int SAMPLE_MULTIPLIER = 25;
  private static final String ACTIVE_VIEW = "active";
  private static final String CRITICAL_VIEW = "critical";
  private static final String SEVERITY_CRITICAL = CRITICAL_VIEW;
  private static final String SEVERITY_WARN = "warn";
  private static final String SEVERITY_INFO = "info";
  private static final String NEW_SPIKES_VIEW = "new-spikes";
  private static final String REFINEMENT_VIEW = "needs-refinement";
  private static final String FACET_CRITICAL = "severity:critical";
  private static final String FACET_WARN = "severity:warn";
  private static final String FACET_SCOPE_ROOM = "scope:room";
  private static final String FACET_MEETING = "scope:meeting";
  private static final String FACET_TOKEN = "category:token";
  private static final String FACET_CONFIG = "category:config";
  private static final String CATEGORY_TOKEN = "TOKEN";
  private static final String CATEGORY_CONFIG = "CONFIG";
  private static final String EXACT_MATCH = "exact-match";
  private static final String CANDIDATE_LIST = "candidate-list";
  private static final String NOT_FOUND = "not-found";

  private final AdminIncidentsReadModel readModel;
  private final AdminIncidentTicketPort ticketPort;
  private final AdminIncidentCoordinationPort coordinationPort;
  private final Clock clock;
  private final String traceUrlTemplate;
  private final Duration retentionWindow;

  public AdminIncidentsService(
      AdminIncidentsReadModel readModel,
      AdminIncidentTicketPort ticketPort,
      AdminIncidentCoordinationPort coordinationPort,
      Clock clock,
      @Value("${app.admin.trace-url-template:}") String traceUrlTemplate,
      @Value("${app.admin.incidents.retention:PT168H}") String retentionWindow) {
    this.readModel = readModel;
    this.ticketPort = ticketPort;
    this.coordinationPort = coordinationPort;
    this.clock = clock;
    this.traceUrlTemplate = traceUrlTemplate;
    this.retentionWindow = Duration.parse(retentionWindow);
  }

  public AdminIncidentListResponse listIncidents(
      String tenantId,
      Collection<String> authorities,
      AdminIncidentListQuery query) {
    ConfigSetEnvironmentType environmentType = IncidentEnvironmentPolicy.resolveEnvironment(query.environment());
    AdminDashboardPeriod period = AdminDashboardPeriod.fromToken(query.period());
    int pageSize = IncidentAggregationPolicy.clampPageSize(query.limit());
    int offset = Math.max(query.offset(), 0);
    List<AdminIncidentsReadModel.IncidentSignal> signals = IncidentListPolicy.loadSignals(
      readModel,
      tenantId,
      environmentType,
      query,
      period,
      clock,
      pageSize,
      offset);
    List<IncidentAggregate> incidents = aggregateIncidents(signals);

    return IncidentListPolicy.resolveResponse(
        incidents,
        tenantId,
        query,
        period,
        environmentType,
        pageSize,
        offset,
        clock);
  }

  public AdminIncidentDetailResponse getIncidentDetail(
      String tenantId,
      Collection<String> authorities,
      String incidentId,
      String environment) {
    ConfigSetEnvironmentType environmentType = IncidentEnvironmentPolicy.resolveEnvironment(environment);
    IncidentAggregate incident = loadIncidentById(tenantId, environmentType, incidentId);
    AdminIncidentTicketPort.TicketingStatus ticketing = ticketPort.describeTicketing(IncidentDetailViewPolicy.toTicketContext(incident));
    AdminIncidentCoordinationPort.CoordinationSnapshot coordination = coordinationPort.describe(
        IncidentDetailViewPolicy.toCoordinationContext(incident));
    boolean fullSubject = IncidentSubjectPolicy.canViewFullSubject(authorities);
    return IncidentDetailPolicy.resolveResponse(
      incident,
      tenantId,
      fullSubject,
      traceUrlTemplate,
      coordination,
      ticketing);
  }

  public AdminIncidentDetailResponse.CoordinationState updateCoordination(
      String tenantId,
      Collection<String> authorities,
      String incidentId,
      String environment,
      com.acme.jitsi.domains.admin.dto.AdminIncidentCoordinationUpdateRequest request,
      String actorId) {
    ConfigSetEnvironmentType environmentType = IncidentEnvironmentPolicy.resolveEnvironment(environment);
    IncidentAggregate incident = loadIncidentById(tenantId, environmentType, incidentId);
    AdminIncidentCoordinationPort.CoordinationSnapshot snapshot = coordinationPort.update(
        new AdminIncidentCoordinationPort.CoordinationUpdateCommand(
            IncidentDetailViewPolicy.toCoordinationContext(incident),
            IncidentCoordinationNormalizationPolicy.normalizeActorId(actorId),
            IncidentNormalizationPolicy.blankToNull(request.owner()),
            IncidentCoordinationNormalizationPolicy.normalizeWorkflowStatus(request.workflowStatus()),
            IncidentNormalizationPolicy.blankToNull(request.ticketReference()),
            IncidentCoordinationNormalizationPolicy.normalizeTicketStatus(request.ticketReference(), request.ticketStatus()),
            IncidentDetailViewPolicy.incidentTraceReference(incident)));
    return IncidentDetailViewPolicy.toCoordinationState(snapshot);
  }

  public AdminIncidentSearchResponse searchIncidents(
      String tenantId,
      Collection<String> authorities,
      AdminIncidentSearchQuery query) {
    ConfigSetEnvironmentType environmentType = IncidentEnvironmentPolicy.resolveEnvironment(query.environment());
    Instant searchFrom = IncidentSearchPolicy.resolveSearchFrom(query, clock, retentionWindow);
    Instant searchTo = IncidentSearchPolicy.resolveSearchTo(query, clock);
    List<AdminIncidentsReadModel.IncidentSignal> signals = readModel.loadSignals(
      IncidentSearchPolicy.toSignalFilter(tenantId, environmentType, searchFrom, searchTo, query));
    List<IncidentAggregate> incidents = aggregateIncidents(signals);

    return IncidentSearchPolicy.resolveResponse(incidents, searchFrom, searchTo, query, clock);
  }

  public AdminIncidentTicketResponse createTicket(
      String tenantId,
      Collection<String> authorities,
      String incidentId,
      String environment,
      String actorId) {
    ConfigSetEnvironmentType environmentType = IncidentEnvironmentPolicy.resolveEnvironment(environment);
    IncidentAggregate incident = loadIncidentById(tenantId, environmentType, incidentId);
    return IncidentTicketPolicy.createTicket(ticketPort, coordinationPort, incident, actorId);
  }

  private IncidentAggregate loadIncidentById(String tenantId, ConfigSetEnvironmentType environmentType, String incidentId) {
    List<AdminIncidentsReadModel.IncidentSignal> signals = IncidentLookupPolicy.loadSignals(
        readModel,
        tenantId,
        environmentType,
        clock,
        retentionWindow,
        IncidentAggregationPolicy.detailLookupSampleLimit());
    return IncidentAggregationPolicy.findById(signals, incidentId);
  }

  private List<IncidentAggregate> aggregateIncidents(List<AdminIncidentsReadModel.IncidentSignal> signals) {
    return IncidentAggregationPolicy.toAggregates(signals);
  }

  private record IncidentAggregate(
      String incidentId,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      String errorCode,
      String category,
      String roomId,
      String meetingId,
      Instant firstOccurredAt,
      Instant lastOccurredAt,
      List<AdminIncidentsReadModel.IncidentSignal> signals,
      Set<String> affectedSubjects,
      String severity) {

    private static IncidentAggregate fromSignals(
        String key,
      List<AdminIncidentsReadModel.IncidentSignal> signals) {
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
      String tenantScope = first.tenantId();
      ConfigSetEnvironmentType signalEnvironmentType = first.environmentType();
      String signalErrorCode = first.errorCode();
      String category = IncidentNormalizationPolicy.normalizeCategory(first.category(), signalErrorCode);
      String errorCode = IncidentNormalizationPolicy.normalizeErrorCode(signalErrorCode);
      String roomId = IncidentNormalizationPolicy.blankToNull(first.roomId());
      String meetingId = IncidentNormalizationPolicy.blankToNull(first.meetingId());
      return new IncidentAggregate(
          IncidentAggregationPolicy.stableIncidentId(key),
          tenantScope,
          signalEnvironmentType,
          errorCode,
          category,
          roomId,
          meetingId,
          firstOccurredAt,
          lastOccurredAt,
          List.copyOf(signals),
          subjects,
          IncidentAggregationPolicy.deriveSeverity(signals, subjects.size()));
    }

    private AdminIncidentListResponse.IncidentListItem toListItem(String tenantScope, Clock clock) {
      return new AdminIncidentListResponse.IncidentListItem(
          incidentId,
          lastOccurredAt.toString(),
          errorCode,
          category,
          tenantScope,
          roomId,
          meetingId,
          affectedSubjects.size(),
          severity,
          IncidentListViewPolicy.buildAffectedEntitySummary(this),
          IncidentListViewPolicy.buildFreshnessHint(this, clock));
    }

    private AdminIncidentDetailResponse toDetailResponse(
        String tenantScope,
        boolean fullSubject,
        String traceUrlTemplate,
        AdminIncidentCoordinationPort.CoordinationSnapshot coordination,
        AdminIncidentTicketPort.TicketingStatus ticketing) {
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts = IncidentDetailViewPolicy.buildAffectedAttempts(
          this,
          fullSubject,
          traceUrlTemplate);
      List<AdminIncidentDetailResponse.RelatedLink> relatedLinks = IncidentDetailPolicy.buildRelatedLinks(this, affectedAttempts);
      List<AdminIncidentDetailResponse.EvidenceBlock> evidence = IncidentDetailPolicy.buildEvidence(affectedAttempts, relatedLinks);
      return new AdminIncidentDetailResponse(
          incidentId,
          tenantScope,
          IncidentEnvironmentPolicy.environmentLabel(environmentType),
          errorCode,
          category,
          severity,
          IncidentDetailViewPolicy.buildSummary(this),
          firstOccurredAt.toString(),
          lastOccurredAt.toString(),
          affectedAttempts,
          IncidentDetailViewPolicy.buildSummaryBar(this),
          IncidentDetailViewPolicy.buildTimeline(affectedAttempts),
          evidence,
          relatedLinks,
          IncidentDetailPolicy.buildNextActions(relatedLinks, evidence),
          IncidentDetailViewPolicy.toCoordinationState(coordination),
          new AdminIncidentDetailResponse.TicketingState(
              ticketing.available(),
              IncidentNormalizationPolicy.firstNonBlank(coordination.ticketReference(), ticketing.ticketKey()),
              IncidentNormalizationPolicy.firstNonBlank(coordination.ticketUrl(), ticketing.ticketUrl()),
              IncidentNormalizationPolicy.firstNonBlank(coordination.ticketStatus(), ticketing.status())));
    }

    private boolean matchesCorrelation(String correlationId) {
      return signals.stream().anyMatch(signal -> correlationId.equals(signal.traceId())
          || correlationId.equals(signal.requestId()));
    }

    private AdminIncidentSearchResponse.SearchCandidate toSearchCandidate() {
      return new AdminIncidentSearchResponse.SearchCandidate(
          incidentId,
          lastOccurredAt.toString(),
          errorCode,
          meetingId);
    }

    private boolean hasId(String candidateIncidentId) {
      return incidentId.equals(candidateIncidentId);
    }

    private boolean shouldRecordTicketLink(AdminIncidentTicketPort.TicketCreationResult result) {
      return IncidentNormalizationPolicy.hasText(result.ticketKey())
          || IncidentNormalizationPolicy.hasText(result.ticketUrl());
    }

    private AdminIncidentCoordinationPort.TicketLinkCommand toTicketLinkCommand(
        String actorId,
        AdminIncidentTicketPort.TicketCreationResult result) {
      return new AdminIncidentCoordinationPort.TicketLinkCommand(
          IncidentDetailViewPolicy.toCoordinationContext(this),
          IncidentCoordinationNormalizationPolicy.normalizeActorId(actorId),
          result.ticketKey(),
          result.created() ? "linked" : "available",
          result.ticketUrl(),
          IncidentDetailViewPolicy.incidentTraceReference(this));
    }

    private AdminIncidentTicketResponse toTicketResponse(AdminIncidentTicketPort.TicketCreationResult result) {
      return new AdminIncidentTicketResponse(
          result.available(),
          result.created(),
          result.ticketKey(),
          result.ticketUrl(),
          result.summary(),
          result.message());
    }
  }

  public record AdminIncidentListQuery(
      String period,
      String environment,
      String savedView,
      String quickFacet,
      String roomId,
      String meetingId,
      String subjectId,
      String errorCode,
      String category,
      String severity,
      int limit,
      int offset,
      String sortBy,
      String direction) {
  }

  public record AdminIncidentSearchQuery(
      String environment,
      String traceId,
      String requestId,
      String errorCode,
      String from,
      String to,
      String meetingId) {
  }

  private static final class IncidentListSortPolicy {

    private static Comparator<IncidentAggregate> resolveSort(String sortBy, String direction) {
      Comparator<IncidentAggregate> comparator = switch (normalizeSort(sortBy)) {
        case "time" -> Comparator.comparing(IncidentAggregate::lastOccurredAt, Comparator.reverseOrder());
        case "severity", "queue" -> Comparator.comparingInt(IncidentListSortPolicy::severityRank)
            .thenComparing(IncidentAggregate::lastOccurredAt, Comparator.reverseOrder());
        default -> Comparator.comparingInt(IncidentListSortPolicy::severityRank)
            .thenComparing(IncidentAggregate::lastOccurredAt, Comparator.reverseOrder());
      };
      return "asc".equalsIgnoreCase(direction) ? comparator.reversed() : comparator;
    }

    private static String normalizeSort(String sortBy) {
      return IncidentNormalizationPolicy.hasText(sortBy) ? sortBy.trim().toLowerCase(Locale.ROOT) : "queue";
    }

    private static int severityRank(IncidentAggregate incident) {
      return switch (incident.severity()) {
        case SEVERITY_CRITICAL -> 0;
        case SEVERITY_WARN -> 1;
        default -> 2;
      };
    }

    private static boolean matchesSeverity(IncidentAggregate incident, String requestedSeverity) {
      return requestedSeverity == null
          || requestedSeverity.isBlank()
          || incident.severity().equalsIgnoreCase(requestedSeverity.trim());
    }
  }

  private static final class IncidentListViewPolicy {

    private static List<AdminIncidentListResponse.SavedView> buildSavedViews() {
      return List.of(
          new AdminIncidentListResponse.SavedView(ACTIVE_VIEW, "Active", "Текущая triage queue для свежих или незакрытых сигналов."),
          new AdminIncidentListResponse.SavedView(CRITICAL_VIEW, "Critical", "Критические кейсы и блокирующие отказы выше остальных."),
          new AdminIncidentListResponse.SavedView(NEW_SPIKES_VIEW, "New spikes", "Свежие всплески по нескольким субъектам или повторяющимся сигналам."),
          new AdminIncidentListResponse.SavedView(REFINEMENT_VIEW, "Needs refinement", "Сигналы с неполным affected scope, где нужен дополнительный drill-down."));
    }

    private static List<AdminIncidentListResponse.QuickFacet> buildQuickFacets(
        List<IncidentAggregate> incidents,
        String quickFacetToken) {
      return List.of(
          quickFacet(incidents, quickFacetToken, FACET_CRITICAL, "Critical", incident -> SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity())),
          quickFacet(incidents, quickFacetToken, FACET_WARN, "Warn", incident -> SEVERITY_WARN.equalsIgnoreCase(incident.severity())),
          quickFacet(incidents, quickFacetToken, FACET_SCOPE_ROOM, "Комнаты", incident -> incident.roomId() != null),
          quickFacet(incidents, quickFacetToken, FACET_MEETING, "Встречи", incident -> incident.meetingId() != null),
          quickFacet(incidents, quickFacetToken, FACET_TOKEN, "Token", incident -> CATEGORY_TOKEN.equalsIgnoreCase(incident.category())),
          quickFacet(incidents, quickFacetToken, FACET_CONFIG, "Config", incident -> CATEGORY_CONFIG.equalsIgnoreCase(incident.category())));
    }

    private static AdminIncidentListResponse.QuickFacet quickFacet(
        List<IncidentAggregate> incidents,
        String quickFacetToken,
        String token,
        String label,
        java.util.function.Predicate<IncidentAggregate> predicate) {
      long count = incidents.stream().filter(predicate).count();
      return new AdminIncidentListResponse.QuickFacet(
          token,
          label,
          count,
          token.equalsIgnoreCase(IncidentNormalizationPolicy.blankToNull(quickFacetToken)));
    }

    private static AdminIncidentListResponse.QueueSort buildQueueSort(String sortBy, String direction) {
      String token = IncidentListSortPolicy.normalizeSort(sortBy);
      String resolvedDirection = "asc".equalsIgnoreCase(direction) ? "asc" : "desc";
      String label = switch (token) {
        case "time" -> "Freshness";
        case "severity", "queue" -> "Severity + freshness";
        default -> "Severity + freshness";
      };
      return new AdminIncidentListResponse.QueueSort(token, label, resolvedDirection);
    }

    private static String buildAffectedEntitySummary(IncidentAggregate incident) {
      long affectedSubjects = incident.affectedSubjects().size();
      String affectedSummary = "%d затронутых субъекта без явной room/meeting привязки".formatted(affectedSubjects);
      if (incident.roomId() != null && incident.meetingId() != null) {
        affectedSummary = "Комната %s, встреча %s, %d затронутых субъекта".formatted(
            incident.roomId(), incident.meetingId(), affectedSubjects);
      } else if (incident.roomId() != null) {
        affectedSummary = "Комната %s, %d затронутых субъекта".formatted(incident.roomId(), affectedSubjects);
      } else if (incident.meetingId() != null) {
        affectedSummary = "Встреча %s, %d затронутых субъекта".formatted(incident.meetingId(), affectedSubjects);
      }
      return affectedSummary;
    }

    private static String buildFreshnessHint(IncidentAggregate incident, Clock clock) {
      Duration age = Duration.between(incident.lastOccurredAt(), Instant.now(clock));
      String relative = formatRelativeAge(age);
      boolean spikeOrCritical = incident.signals().size() >= 3 || SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity());
      String hintTemplate = spikeOrCritical ? "Последний всплеск %s" : "Активность %s";
      return hintTemplate.formatted(relative);
    }

    private static String deriveOperationalStatus(IncidentAggregate incident) {
      boolean blocked = incident.signals().stream()
          .anyMatch(signal -> "blocked".equalsIgnoreCase(IncidentNormalizationPolicy.blankToNull(signal.joinReadinessStatus())));
      String operationalStatus = "active-investigation";
      if (blocked) {
        operationalStatus = "blocked";
      } else if (incident.signals().isEmpty()) {
        operationalStatus = "monitoring";
      }
      return operationalStatus;
    }

    private static String buildTimelineSummary(AdminIncidentDetailResponse.AffectedAttempt attempt) {
      List<String> parts = new ArrayList<>();
      if (IncidentNormalizationPolicy.hasText(attempt.role())) {
        parts.add(attempt.role());
      }
      if (IncidentNormalizationPolicy.hasText(attempt.subjectDisplay())) {
        parts.add(attempt.subjectDisplay());
      }
      String summary = "Детализация попытки входа доступна в технических деталях";
      if (!parts.isEmpty()) {
        summary = String.join(" · ", parts);
      }
      return summary;
    }

    private static String formatRelativeAge(Duration age) {
      long minutes = Math.max(age.toMinutes(), 0L);
      long hours = Math.max(age.toHours(), 1L);
      long days = Math.max(age.toDays(), 1L);
      String relativeAge = "%d дн назад".formatted(days);
      boolean justNow = minutes < 1;
      boolean withinHour = minutes < 60;
      boolean withinDay = hours < 24;
      if (justNow) {
        relativeAge = "только что";
      } else if (withinHour) {
        relativeAge = "%d мин назад".formatted(minutes);
      } else if (withinDay) {
        relativeAge = "%d ч назад".formatted(hours);
      }
      return relativeAge;
    }
  }

  private static final class IncidentListPolicy {

    private static List<AdminIncidentsReadModel.IncidentSignal> loadSignals(
        AdminIncidentsReadModel readModel,
        String tenantId,
        ConfigSetEnvironmentType environmentType,
        AdminIncidentListQuery query,
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
          IncidentAggregationPolicy.rawSampleLimit(pageSize, offset)));
    }

    private static AdminIncidentListResponse resolveResponse(
        List<IncidentAggregate> incidents,
        String tenantId,
        AdminIncidentListQuery query,
        AdminDashboardPeriod period,
        ConfigSetEnvironmentType environmentType,
        int pageSize,
        int offset,
        Clock clock) {
      String selectedView = resolveSavedView(query.savedView(), query);
      List<IncidentAggregate> viewScoped = incidents.stream()
          .filter(incident -> matchesSavedView(incident, selectedView, clock))
          .toList();
      String quickFacetToken = resolveQuickFacet(query.quickFacet(), query);
      List<AdminIncidentListResponse.QuickFacet> quickFacets = IncidentListViewPolicy.buildQuickFacets(viewScoped, quickFacetToken);
      List<IncidentAggregate> filtered = filterIncidents(viewScoped, quickFacetToken, query);
      List<AdminIncidentListResponse.IncidentListItem> items = pageItems(filtered, offset, pageSize, tenantId, clock);

      return new AdminIncidentListResponse(
          period.token(),
          IncidentEnvironmentPolicy.environmentLabel(environmentType),
          tenantId,
          Instant.now(clock).toString(),
          selectedView,
          quickFacetToken,
          IncidentListViewPolicy.buildSavedViews(),
          quickFacets,
          IncidentListViewPolicy.buildQueueSort(query.sortBy(), query.direction()),
          pageSize,
          offset,
          filtered.size(),
          items);
    }

    private static List<IncidentAggregate> filterIncidents(
        List<IncidentAggregate> viewScoped,
        String quickFacetToken,
        AdminIncidentListQuery query) {
      return viewScoped.stream()
          .filter(incident -> matchesQuickFacet(incident, quickFacetToken))
          .filter(incident -> IncidentListSortPolicy.matchesSeverity(incident, query.severity()))
          .sorted(IncidentListSortPolicy.resolveSort(query.sortBy(), query.direction()))
          .toList();
    }

    private static List<AdminIncidentListResponse.IncidentListItem> pageItems(
        List<IncidentAggregate> filtered,
        int offset,
        int pageSize,
        String tenantId,
        Clock clock) {
      return filtered.stream()
          .skip(offset)
          .limit(pageSize)
          .map(incident -> incident.toListItem(tenantId, clock))
          .toList();
    }

    private static boolean matchesSavedView(IncidentAggregate incident, String savedView, Clock clock) {
      return switch (savedView) {
        case CRITICAL_VIEW -> SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity());
        case NEW_SPIKES_VIEW -> incident.signals().size() >= 3 || incident.affectedSubjects().size() >= 3;
        case REFINEMENT_VIEW -> incident.roomId() == null || incident.meetingId() == null;
        case ACTIVE_VIEW -> isActiveIncident(incident, clock);
        default -> true;
      };
    }

    private static boolean isActiveIncident(IncidentAggregate incident, Clock clock) {
      Duration age = Duration.between(incident.lastOccurredAt(), Instant.now(clock));
      return !age.isNegative() && (age.compareTo(Duration.ofHours(6)) <= 0 || !SEVERITY_INFO.equalsIgnoreCase(incident.severity()));
    }

    private static String resolveSavedView(String requestedView, AdminIncidentListQuery query) {
      String savedView = ACTIVE_VIEW;
      boolean criticalSeverity = SEVERITY_CRITICAL.equalsIgnoreCase(IncidentNormalizationPolicy.blankToNull(query.severity()));
      if (IncidentNormalizationPolicy.hasText(requestedView) && isSupportedSavedView(requestedView)) {
        savedView = requestedView.trim().toLowerCase(Locale.ROOT);
      } else if (criticalSeverity) {
        savedView = CRITICAL_VIEW;
      }
      return savedView;
    }

    private static boolean isSupportedSavedView(String token) {
      return switch (token.trim().toLowerCase(Locale.ROOT)) {
        case ACTIVE_VIEW, CRITICAL_VIEW, NEW_SPIKES_VIEW, REFINEMENT_VIEW -> true;
        default -> false;
      };
    }

    private static String resolveQuickFacet(String requestedFacet, AdminIncidentListQuery query) {
      String quickFacetToken = null;
      if (IncidentNormalizationPolicy.hasText(requestedFacet) && isSupportedQuickFacet(requestedFacet)) {
        quickFacetToken = requestedFacet.trim().toLowerCase(Locale.ROOT);
      } else if (IncidentNormalizationPolicy.hasText(query.meetingId())) {
        quickFacetToken = FACET_MEETING;
      } else if (IncidentNormalizationPolicy.hasText(query.roomId())) {
        quickFacetToken = FACET_SCOPE_ROOM;
      } else if (SEVERITY_CRITICAL.equalsIgnoreCase(IncidentNormalizationPolicy.blankToNull(query.severity()))) {
        quickFacetToken = FACET_CRITICAL;
      } else {
        String categoryKey = IncidentNormalizationPolicy.normalizeCategory(query.category(), null);
        if (CATEGORY_TOKEN.equals(categoryKey)) {
          quickFacetToken = FACET_TOKEN;
        } else if (CATEGORY_CONFIG.equals(categoryKey)) {
          quickFacetToken = FACET_CONFIG;
        }
      }
      return quickFacetToken;
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

    private static boolean matchesQuickFacet(IncidentAggregate incident, String quickFacetToken) {
      boolean matches = true;
      if (IncidentNormalizationPolicy.hasText(quickFacetToken)) {
        matches = switch (quickFacetToken.trim().toLowerCase(Locale.ROOT)) {
          case FACET_CRITICAL -> SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity());
          case FACET_WARN -> SEVERITY_WARN.equalsIgnoreCase(incident.severity());
          case FACET_SCOPE_ROOM -> incident.roomId() != null;
          case FACET_MEETING -> incident.meetingId() != null;
          case FACET_TOKEN -> CATEGORY_TOKEN.equalsIgnoreCase(incident.category());
          case FACET_CONFIG -> CATEGORY_CONFIG.equalsIgnoreCase(incident.category());
          default -> true;
        };
      }
      return matches;
    }
  }

  private static final class IncidentDetailViewPolicy {

    private static List<AdminIncidentDetailResponse.AffectedAttempt> buildAffectedAttempts(
        IncidentAggregate incident,
        boolean fullSubject,
        String traceUrlTemplate) {
      return incident.signals().stream()
          .sorted(Comparator.comparing(AdminIncidentsReadModel.IncidentSignal::occurredAt).reversed())
          .map(signal -> new AdminIncidentDetailResponse.AffectedAttempt(
              signal.occurredAt().toString(),
              signal.traceId(),
              IncidentNormalizationPolicy.firstNonBlank(signal.requestId(), signal.traceId()),
              fullSubject
                  ? IncidentNormalizationPolicy.blankToNull(signal.subjectId())
                  : IncidentSubjectPolicy.maskSubject(signal.subjectId()),
              subjectFilterValue(signal, fullSubject),
              IncidentNormalizationPolicy.blankToNull(signal.role()),
              IncidentNormalizationPolicy.blankToNull(signal.diagnosticResult()),
              IncidentNormalizationPolicy.blankToNull(signal.roomId()),
              IncidentNormalizationPolicy.blankToNull(signal.meetingId()),
              buildTraceUrl(signal.traceId(), traceUrlTemplate)))
          .toList();
    }

    private static String subjectFilterValue(AdminIncidentsReadModel.IncidentSignal signal, boolean fullSubject) {
      return fullSubject
          ? IncidentNormalizationPolicy.blankToNull(signal.subjectId())
          : IncidentNormalizationPolicy.blankToNull("");
    }

    private static AdminIncidentDetailResponse.SummaryBar buildSummaryBar(IncidentAggregate incident) {
      return new AdminIncidentDetailResponse.SummaryBar(
          "%s incident".formatted(incident.errorCode()),
          "%s / %s".formatted(incident.errorCode(), incident.category()),
          IncidentListViewPolicy.buildAffectedEntitySummary(incident),
          IncidentListViewPolicy.deriveOperationalStatus(incident),
          "%s → %s".formatted(incident.firstOccurredAt(), incident.lastOccurredAt()),
          IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()));
    }

    private static List<AdminIncidentDetailResponse.TimelineEntry> buildTimeline(
        List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
      return affectedAttempts.stream()
          .map(attempt -> new AdminIncidentDetailResponse.TimelineEntry(
              attempt.occurredAt(),
              "Повторный отказ входа",
              IncidentListViewPolicy.buildTimelineSummary(attempt),
              attempt.subjectDisplay(),
              attempt.role(),
              attempt.traceId(),
              attempt.correlationId(),
              attempt.roomId(),
              attempt.meetingId()))
          .toList();
    }

    private static AdminIncidentTicketPort.TicketContext toTicketContext(IncidentAggregate incident) {
      return new AdminIncidentTicketPort.TicketContext(
          incident.incidentId(),
          incident.tenantId(),
          IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()),
          incident.errorCode(),
          incident.category(),
          incidentTraceReference(incident),
          buildSummary(incident));
    }

    private static AdminIncidentCoordinationPort.CoordinationContext toCoordinationContext(IncidentAggregate incident) {
      return new AdminIncidentCoordinationPort.CoordinationContext(
          incident.incidentId(),
          incident.tenantId(),
          IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()));
    }

    private static AdminIncidentDetailResponse.CoordinationState toCoordinationState(
        AdminIncidentCoordinationPort.CoordinationSnapshot snapshot) {
      return new AdminIncidentDetailResponse.CoordinationState(
          snapshot.enabled(),
          snapshot.availability(),
          snapshot.explanation(),
          snapshot.owner(),
          snapshot.workflowStatus(),
          snapshot.ticketReference(),
          snapshot.ticketStatus(),
          snapshot.ticketUrl(),
          snapshot.history().stream()
              .map(entry -> new AdminIncidentDetailResponse.CoordinationAuditEntry(
                  entry.occurredAt(),
                  entry.actorId(),
                  entry.actionType(),
                  entry.traceId(),
                  entry.fromState(),
                  entry.toState()))
              .toList());
    }

    private static String incidentTraceReference(IncidentAggregate incident) {
      return incident.signals().stream()
          .map(signal -> IncidentNormalizationPolicy.firstNonBlank(signal.traceId(), signal.requestId()))
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
    }

    private static String buildSummary(IncidentAggregate incident) {
      return "%s incident for %s (%s)".formatted(incident.errorCode(), incident.tenantId(), incident.severity());
    }

    private static String buildTraceUrl(String traceId, String traceUrlTemplate) {
      String traceUrl = null;
      if (IncidentNormalizationPolicy.hasText(traceId) && IncidentNormalizationPolicy.hasText(traceUrlTemplate)) {
        traceUrl = traceUrlTemplate.replace("{traceId}", traceId);
      }
      return traceUrl;
    }
  }

  private static final class IncidentDetailPolicy {

    private static AdminIncidentDetailResponse resolveResponse(
        IncidentAggregate incident,
        String tenantId,
        boolean fullSubject,
        String traceUrlTemplate,
        AdminIncidentCoordinationPort.CoordinationSnapshot coordination,
        AdminIncidentTicketPort.TicketingStatus ticketing) {
      return incident.toDetailResponse(tenantId, fullSubject, traceUrlTemplate, coordination, ticketing);
    }

    private static List<AdminIncidentDetailResponse.EvidenceBlock> buildEvidence(
        List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts,
        List<AdminIncidentDetailResponse.RelatedLink> relatedLinks) {
      AdminIncidentDetailResponse.AffectedAttempt diagAttempt = findAttemptWithDiagnostics(affectedAttempts);
      AdminIncidentDetailResponse.AffectedAttempt corrAttempt = findAttemptWithCorrelation(affectedAttempts);
      return List.of(
        buildDiagnosticsEvidence(diagAttempt),
        buildCorrelationEvidence(corrAttempt, relatedLinks));
    }

    private static AdminIncidentDetailResponse.AffectedAttempt findAttemptWithDiagnostics(
        List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
      return affectedAttempts.stream()
          .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.diagnosticResult()))
          .findFirst()
          .orElseGet(() -> firstAttemptOrNull(affectedAttempts));
    }

    private static AdminIncidentDetailResponse.AffectedAttempt findAttemptWithCorrelation(
        List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
      return affectedAttempts.stream()
          .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.traceId())
              || IncidentNormalizationPolicy.hasText(attempt.correlationId()))
          .findFirst()
          .orElseGet(() -> firstAttemptOrNull(affectedAttempts));
    }

    private static AdminIncidentDetailResponse.AffectedAttempt firstAttemptOrNull(
        List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
      return affectedAttempts.stream().findFirst().orElse(null);
    }

    private static AdminIncidentDetailResponse.EvidenceBlock buildDiagnosticsEvidence(
        AdminIncidentDetailResponse.AffectedAttempt attempt) {
      boolean noDiagnostics = attempt == null || !IncidentNormalizationPolicy.hasText(attempt.diagnosticResult());
      AdminIncidentDetailResponse.EvidenceBlock evidenceBlock;
      if (noDiagnostics) {
        evidenceBlock = new AdminIncidentDetailResponse.EvidenceBlock(
            "diagnostics",
            "Diagnostics result",
            "empty",
            null,
            "Диагностический результат для инцидента не был зафиксирован в bounded read model.",
            attempt == null ? null : attempt.traceId(),
            attempt == null ? null : attempt.correlationId(),
            attempt == null ? null : attempt.traceUrl(),
            new AdminIncidentDetailResponse.EmptyState(
                "Нет diagnostics result",
                "Используйте trace/correlation context или role history как следующий bounded источник доказательств.",
                "Открыть историю ролей",
                "role-history"));
      } else {
        AdminIncidentDetailResponse.AffectedAttempt diagnosticsAttempt = Objects.requireNonNull(attempt);
        evidenceBlock = new AdminIncidentDetailResponse.EvidenceBlock(
            "diagnostics",
            "Diagnostics result",
            "available",
            diagnosticsAttempt.diagnosticResult(),
            "Diagnostics evidence подготовлен для first-scan без raw payload dump.",
            diagnosticsAttempt.traceId(),
            diagnosticsAttempt.correlationId(),
            diagnosticsAttempt.traceUrl(),
            null);
      }
      return evidenceBlock;
    }

    private static AdminIncidentDetailResponse.EvidenceBlock buildCorrelationEvidence(
        AdminIncidentDetailResponse.AffectedAttempt attempt,
        List<AdminIncidentDetailResponse.RelatedLink> relatedLinks) {
      AdminIncidentDetailResponse.RelatedLink traceLink = relatedLinks.stream()
          .filter(link -> "trace".equals(link.kind()))
          .findFirst()
          .orElse(null);
        boolean noCorrelation = attempt == null || (!IncidentNormalizationPolicy.hasText(attempt.traceId())
          && !IncidentNormalizationPolicy.hasText(attempt.correlationId()));
      AdminIncidentDetailResponse.EvidenceBlock evidenceBlock;
        if (noCorrelation) {
        evidenceBlock = new AdminIncidentDetailResponse.EvidenceBlock(
            "correlation",
            "Trace и correlation context",
            "empty",
            null,
            "Для этого инцидента отсутствует trace или correlation identifier, поэтому UI должен показать bounded empty state вместо пустой секции.",
            null,
            null,
            null,
            new AdminIncidentDetailResponse.EmptyState(
                "Нет trace link",
                "Откройте role history или вернитесь в incident queue, чтобы продолжить расследование по связанным сущностям.",
                "Вернуться в очередь",
                "queue-return"));
      } else if (traceLink != null && IncidentNormalizationPolicy.hasText(traceLink.externalUrl())) {
        AdminIncidentDetailResponse.AffectedAttempt correlationAttempt = Objects.requireNonNull(attempt);
        evidenceBlock = new AdminIncidentDetailResponse.EvidenceBlock(
            "correlation",
            "Trace и correlation context",
            "available",
          IncidentNormalizationPolicy.firstNonBlank(correlationAttempt.traceId(), correlationAttempt.correlationId()),
            "Trace link уже нормализован и готов для drill-through без ручного повторного поиска.",
          correlationAttempt.traceId(),
          correlationAttempt.correlationId(),
            traceLink.externalUrl(),
            null);
      } else {
        AdminIncidentDetailResponse.AffectedAttempt correlationAttempt = Objects.requireNonNull(attempt);
        evidenceBlock = new AdminIncidentDetailResponse.EvidenceBlock(
            "correlation",
            "Trace и correlation context",
            "copy-only",
          IncidentNormalizationPolicy.firstNonBlank(correlationAttempt.traceId(), correlationAttempt.correlationId()),
            "Trace URL недоступен, но trace/correlation data остаются пригодными для копирования и bounded ручного drill-through.",
          correlationAttempt.traceId(),
          correlationAttempt.correlationId(),
            null,
            null);
      }
      return evidenceBlock;
    }

    private static List<AdminIncidentDetailResponse.RelatedLink> buildRelatedLinks(
        IncidentAggregate incident,
        List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
      List<AdminIncidentDetailResponse.RelatedLink> links = new ArrayList<>();
        AdminIncidentDetailResponse.AffectedAttempt historyAttempt = affectedAttempts.stream()
          .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.subjectIdFilterValue())
              || IncidentNormalizationPolicy.hasText(attempt.roomId())
              || IncidentNormalizationPolicy.hasText(attempt.meetingId()))
          .findFirst()
          .orElse(null);
        if (historyAttempt != null) {
        links.add(new AdminIncidentDetailResponse.RelatedLink(
            "role-history",
            "История ролей по субъекту",
            IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()),
          historyAttempt.subjectIdFilterValue(),
          historyAttempt.roomId(),
          historyAttempt.meetingId(),
          historyAttempt.traceId(),
            null));
      }
        AdminIncidentDetailResponse.AffectedAttempt scopeAttempt = affectedAttempts.stream()
          .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.roomId())
              || IncidentNormalizationPolicy.hasText(attempt.meetingId()))
          .findFirst()
          .orElse(null);
        if (scopeAttempt != null) {
        links.add(new AdminIncidentDetailResponse.RelatedLink(
            "incident-scope",
            "Очередь по затронутой сущности",
            IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()),
          scopeAttempt.subjectIdFilterValue(),
          scopeAttempt.roomId(),
          scopeAttempt.meetingId(),
          scopeAttempt.traceId(),
            null));
      }
      AdminIncidentDetailResponse.AffectedAttempt traceAttempt = affectedAttempts.stream()
          .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.traceUrl()))
          .findFirst()
          .orElse(null);
      if (traceAttempt != null) {
        links.add(new AdminIncidentDetailResponse.RelatedLink(
            "trace",
            "Открыть trace",
            IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()),
            traceAttempt.subjectIdFilterValue(),
            traceAttempt.roomId(),
            traceAttempt.meetingId(),
            traceAttempt.traceId(),
            traceAttempt.traceUrl()));
      }
      return List.copyOf(links);
    }

    private static List<AdminIncidentDetailResponse.NextAction> buildNextActions(
        List<AdminIncidentDetailResponse.RelatedLink> relatedLinks,
        List<AdminIncidentDetailResponse.EvidenceBlock> evidence) {
      List<AdminIncidentDetailResponse.NextAction> actions = new ArrayList<>();
      actions.add(new AdminIncidentDetailResponse.NextAction(
          "queue",
          "Вернуться в очередь",
          "Сохранить incident context и продолжить triage из queue-first surface.",
          "queue-return",
          null));

      relatedLinks.stream()
          .filter(link -> "role-history".equals(link.kind()))
          .findFirst()
          .ifPresent(link -> actions.add(new AdminIncidentDetailResponse.NextAction(
              "role-history",
              "Открыть историю ролей",
              "Перейти к связанному role-history context без ручного воспроизведения filters.",
              "role-history",
              null)));

      relatedLinks.stream()
          .filter(link -> "trace".equals(link.kind()) && IncidentNormalizationPolicy.hasText(link.externalUrl()))
          .findFirst()
          .ifPresent(link -> actions.add(new AdminIncidentDetailResponse.NextAction(
              "trace",
              "Открыть trace",
              "Перейти в trace tool с уже сохранённым incident context.",
              "external-trace",
              link.externalUrl())));

        boolean manualCorrelation = evidence.stream()
          .anyMatch(block -> "correlation".equals(block.kind()) && "copy-only".equals(block.status()));
        if (manualCorrelation) {
        actions.add(new AdminIncidentDetailResponse.NextAction(
            "correlation",
            "Использовать trace/correlation data",
            "Скопируйте trace или correlation identifier и продолжите bounded drill-through вручную.",
            "copy-correlation",
            null));
      }
      return List.copyOf(actions);
    }
  }

  private static final class IncidentSearchPolicy {

    private static final String NO_MATCH_MESSAGE = "Совпадений не найдено. Уточните время, tenant или entity filters.";
    private static final String REFINE_FILTERS_MESSAGE = "Уточните tenant или entity filters.";

    private static AdminIncidentSearchResponse resolveResponse(
        List<IncidentAggregate> incidents,
        Instant searchFrom,
        Instant searchTo,
        AdminIncidentSearchQuery query,
        Clock clock) {
      String correlationId = IncidentNormalizationPolicy.firstNonBlank(query.traceId(), query.requestId());
      if (correlationId != null) {
        return resolveCorrelationResponse(incidents, correlationId);
      }

      Instant targetInstant = resolveSearchTarget(searchFrom, searchTo, query, clock);
      List<AdminIncidentSearchResponse.SearchCandidate> candidates = incidents.stream()
          .sorted(candidateComparator(query, targetInstant))
          .map(IncidentAggregate::toSearchCandidate)
          .toList();
      return resolveCandidateResponse(candidates);
    }

    private static AdminIncidentSearchResponse resolveCorrelationResponse(
        List<IncidentAggregate> incidents,
        String correlationId) {
      return incidents.stream()
          .filter(incident -> incident.matchesCorrelation(correlationId))
          .findFirst()
          .map(incident -> exactMatch(incident.incidentId()))
          .orElseGet(IncidentSearchPolicy::notFound);
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
        AdminIncidentSearchQuery query) {
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
          IncidentAggregationPolicy.rawSampleLimit(DEFAULT_PAGE_SIZE, 0));
    }

    private static Instant resolveSearchFrom(AdminIncidentSearchQuery query, Clock clock, Duration retentionWindow) {
      Instant searchFrom = Instant.now(clock).minus(retentionWindow);
      if (IncidentNormalizationPolicy.hasText(query.from())) {
        searchFrom = parseSearchInstant(query.from().trim(), "from");
      }
      return searchFrom;
    }

    private static Instant resolveSearchTo(AdminIncidentSearchQuery query, Clock clock) {
      Instant searchTo = Instant.now(clock);
      if (IncidentNormalizationPolicy.hasText(query.to())) {
        searchTo = parseSearchInstant(query.to().trim(), "to");
      }
      return searchTo;
    }

    private static Instant resolveSearchTarget(
        Instant from,
        Instant to,
        AdminIncidentSearchQuery query,
        Clock clock) {
      Instant explicitFrom = IncidentNormalizationPolicy.hasText(query.from()) ? from : null;
      Instant explicitTo = IncidentNormalizationPolicy.hasText(query.to()) ? to : null;
      Instant targetInstant = Instant.now(clock);
      if (explicitFrom != null && explicitTo != null) {
        targetInstant = explicitFrom.plusMillis(Duration.between(explicitFrom, explicitTo).toMillis() / 2L);
      } else if (explicitFrom != null) {
        targetInstant = explicitFrom;
      } else if (explicitTo != null) {
        targetInstant = explicitTo;
      }
      return targetInstant;
    }

    private static Instant parseSearchInstant(String value, String parameterName) {
      try {
        return Instant.parse(value);
      } catch (DateTimeParseException ex) {
        throw new AdminIncidentsInvalidRequestException(
            "Параметр %s должен быть ISO-8601 timestamp.".formatted(parameterName),
            ex);
      }
    }

    private static Comparator<IncidentAggregate> candidateComparator(
        AdminIncidentSearchQuery query,
        Instant targetInstant) {
      return Comparator.comparingInt((IncidentAggregate incident) -> entityReferenceScore(incident, query))
          .reversed()
          .thenComparingLong(incident -> timeDistanceMillis(incident, targetInstant))
          .thenComparing(IncidentAggregate::lastOccurredAt, Comparator.reverseOrder());
    }

    private static int entityReferenceScore(IncidentAggregate incident, AdminIncidentSearchQuery query) {
      int score = 0;
      if (IncidentNormalizationPolicy.hasText(query.meetingId())
          && query.meetingId().trim().equalsIgnoreCase(IncidentNormalizationPolicy.blankToNull(incident.meetingId()))) {
        score += 1;
      }
      return score;
    }

    private static long timeDistanceMillis(IncidentAggregate incident, Instant targetInstant) {
      long fromStart = Math.abs(Duration.between(incident.firstOccurredAt(), targetInstant).toMillis());
      long fromEnd = Math.abs(Duration.between(incident.lastOccurredAt(), targetInstant).toMillis());
      return Math.min(fromStart, fromEnd);
    }
  }

  private static final class IncidentTicketPolicy {

    private static AdminIncidentTicketResponse createTicket(
        AdminIncidentTicketPort ticketPort,
        AdminIncidentCoordinationPort coordinationPort,
        IncidentAggregate incident,
        String actorId) {
      AdminIncidentTicketPort.TicketCreationResult result =
          ticketPort.createTicket(IncidentDetailViewPolicy.toTicketContext(incident));
      if (incident.shouldRecordTicketLink(result)) {
        coordinationPort.recordTicketLink(incident.toTicketLinkCommand(actorId, result));
      }
      return incident.toTicketResponse(result);
    }
  }

  private static final class IncidentLookupPolicy {

    private static List<AdminIncidentsReadModel.IncidentSignal> loadSignals(
        AdminIncidentsReadModel readModel,
        String tenantId,
        ConfigSetEnvironmentType environmentType,
        Clock clock,
        Duration retentionWindow,
        int sampleLimit) {
      Instant lookupTo = Instant.now(clock);
      return readModel.loadSignals(new AdminIncidentsReadModel.SignalFilter(
          tenantId,
          environmentType,
          lookupTo.minus(retentionWindow),
          lookupTo,
          null,
          null,
          null,
          null,
          null,
          sampleLimit));
    }
  }

  private static final class IncidentAggregationPolicy {

    private static IncidentAggregate findById(
        List<AdminIncidentsReadModel.IncidentSignal> signals,
        String incidentId) {
      return toAggregates(signals).stream()
          .filter(incident -> incident.hasId(incidentId))
          .findFirst()
          .orElseThrow(() -> new AdminIncidentNotFoundException(
              "Инцидент '%s' не найден в retention window.".formatted(incidentId)));
    }

    private static List<IncidentAggregate> toAggregates(List<AdminIncidentsReadModel.IncidentSignal> signals) {
      return groupSignals(signals).stream()
          .map(GroupedIncident::toAggregate)
          .toList();
    }

    private static List<GroupedIncident> groupSignals(List<AdminIncidentsReadModel.IncidentSignal> signals) {
      Map<String, List<AdminIncidentsReadModel.IncidentSignal>> grouped = new LinkedHashMap<>();
      for (AdminIncidentsReadModel.IncidentSignal signal : signals) {
        String category = IncidentNormalizationPolicy.normalizeCategory(signal.category(), signal.errorCode());
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

    private record GroupedIncident(String key, List<AdminIncidentsReadModel.IncidentSignal> signals) {

      private IncidentAggregate toAggregate() {
        return IncidentAggregate.fromSignals(key, signals);
      }
    }

    private static String deriveSeverity(List<AdminIncidentsReadModel.IncidentSignal> signals, int uniqueSubjects) {
      boolean criticalImpact = hasCriticalImpact(signals, uniqueSubjects);
      boolean warningImpact = hasWarningImpact(signals, uniqueSubjects);
      String severity = SEVERITY_INFO;
      if (criticalImpact) {
        severity = SEVERITY_CRITICAL;
      } else if (warningImpact) {
        severity = SEVERITY_WARN;
      }
      return severity;
    }

    private static boolean hasCriticalImpact(
        List<AdminIncidentsReadModel.IncidentSignal> signals,
        int uniqueSubjects) {
      boolean highVolume = uniqueSubjects >= 10 || signals.size() >= 20;
      boolean criticalAlert = signals.stream().anyMatch(signal -> SEVERITY_CRITICAL.equalsIgnoreCase(signal.alertSeverity()));
      boolean blocked = signals.stream().anyMatch(signal -> "blocked".equalsIgnoreCase(signal.joinReadinessStatus()));
      boolean configMismatch = signals.stream().anyMatch(signal -> "CONFIG_INCOMPATIBLE".equalsIgnoreCase(signal.errorCode()));
      boolean prodOrTest = signals.stream().anyMatch(signal -> signal.environmentType() == ConfigSetEnvironmentType.PROD
          || signal.environmentType() == ConfigSetEnvironmentType.TEST);
      return criticalAlert || blocked || configMismatch || (prodOrTest && highVolume);
    }

    private static boolean hasWarningImpact(
        List<AdminIncidentsReadModel.IncidentSignal> signals,
        int uniqueSubjects) {
      boolean warningAlert = signals.stream().anyMatch(signal -> "warning".equalsIgnoreCase(signal.alertSeverity()));
      return warningAlert || uniqueSubjects >= 3 || signals.size() >= 3;
    }

    private static int clampPageSize(int limit) {
      int pageSize = DEFAULT_PAGE_SIZE;
      if (limit > 0) {
        pageSize = Math.min(limit, MAX_PAGE_SIZE);
      }
      return pageSize;
    }

    private static int rawSampleLimit(int limit, int offset) {
      return Math.max((offset + clampPageSize(limit)) * SAMPLE_MULTIPLIER, 500);
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
      return String.join("|",
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
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash).substring(0, 22);
      } catch (NoSuchAlgorithmException ex) {
        throw new IllegalStateException("SHA-256 is not available", ex);
      }
    }
  }
}