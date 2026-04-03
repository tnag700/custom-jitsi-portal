package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AdminDashboardSummaryComposer {

  private static final String OPEN_INCIDENTS = "Открыть очередь инцидентов";
  private static final String SEVERITY_INFO = "info";
  private static final String SEVERITY_WARNING = "warning";
  private static final String SEVERITY_CRITICAL = "critical";

  private final String incidentDetailUrl;
  private final AdminDashboardHandoffFactory handoffFactory;
  private final AdminDashboardSummarySignalSupport signalSupport;
  private final AdminDashboardStatusBuilder statusBuilder;
  private final AdminDashboardDegradationBuilder degradations;

  AdminDashboardSummaryComposer(String incidentDetailUrl) {
    this.incidentDetailUrl = incidentDetailUrl;
    this.handoffFactory = new AdminDashboardHandoffFactory();
    this.signalSupport = new AdminDashboardSummarySignalSupport();
    this.statusBuilder = new AdminDashboardStatusBuilder(handoffFactory, signalSupport);
    this.degradations = new AdminDashboardDegradationBuilder(handoffFactory, signalSupport);
  }

  AdminDashboardSummaryResponse compose(SummaryRequest request) {
    List<AdminDashboardSummaryResponse.DegradationSummary> topDegradations = degradations.buildTopDegradations(
        request.health(),
        request.joinReadiness(),
        request.activeConfigSet(),
        request.compatibility(),
        request.overview(),
        request.environment(),
        request.period(),
        request.roomId(),
        request.meetingId());
      List<AdminDashboardSummaryResponse.ServiceStatus> serviceStatuses = statusBuilder.buildServiceStatuses(
        request.health(),
        request.joinReadiness(),
        request.activeConfigSet(),
        request.compatibility(),
        request.environment(),
        request.period(),
        request.roomId(),
        request.meetingId());
    List<AdminDashboardSummaryResponse.LatestSpike> latestSpikes = buildLatestSpikes(
        request.overview(),
        request.environment(),
        request.period(),
        request.roomId(),
        request.meetingId());
    List<AdminDashboardSummaryResponse.AffectedScopeSummary> scopeSummary = buildAffectedScopeSummary(
        request.overview(),
        request.environment(),
        request.period(),
        request.roomId(),
        request.meetingId());

    return new AdminDashboardSummaryResponse(
        request.period(),
        request.environment(),
        request.tenantId(),
        request.generatedAt().toString(),
        request.traceId(),
        buildPriorityBanner(topDegradations, request.environment(), request.period(), request.roomId(), request.meetingId()),
        topDegradations,
        serviceStatuses,
        latestSpikes,
        scopeSummary,
        buildSafeStateSummary(topDegradations.isEmpty(), latestSpikes, request.environment(), request.period()),
        new AdminDashboardSummaryResponse.EntityFilter(request.roomId(), request.meetingId()),
        request.overview().sampleWindowLimited());
  }

  private AdminDashboardSummaryResponse.PriorityBanner buildPriorityBanner(
      List<AdminDashboardSummaryResponse.DegradationSummary> topDegradations,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    AdminDashboardSummaryResponse.PriorityBanner banner;
    if (topDegradations.isEmpty()) {
      banner = new AdminDashboardSummaryResponse.PriorityBanner(
          false,
          "none",
          "Операционный контур стабилен",
          "Активных деградаций не обнаружено. При необходимости можно открыть очередь инцидентов или вторичные модули.",
          OPEN_INCIDENTS,
          handoffFactory.buildHandoffContext(environment, period, SEVERITY_INFO, null, null, roomId, meetingId, null));
    } else {
      AdminDashboardSummaryResponse.DegradationSummary top = topDegradations.get(0);
      banner = new AdminDashboardSummaryResponse.PriorityBanner(
          true,
          top.severity(),
          top.title(),
          top.summary(),
          top.actionLabel(),
          top.handoff());
    }
    return banner;
  }

  private List<AdminDashboardSummaryResponse.LatestSpike> buildLatestSpikes(
      AdminDashboardReadModel.JoinAuditOverview overview,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    return overview.topErrorCodes().stream()
        .limit(3)
        .map(count -> {
          String category = signalSupport.findCategoryForErrorCode(overview, count.key());
          String severity = count.count() >= 3 ? SEVERITY_CRITICAL : SEVERITY_WARNING;
          return new AdminDashboardSummaryResponse.LatestSpike(
              count.key(),
              category,
              count.count(),
              "%d свежих отказов за окно %s связаны с %s.".formatted(count.count(), period, count.key()),
              handoffFactory.buildHandoffContext(environment, period, severity, count.key(), category, roomId, meetingId, null));
        })
        .toList();
  }

  private List<AdminDashboardSummaryResponse.AffectedScopeSummary> buildAffectedScopeSummary(
      AdminDashboardReadModel.JoinAuditOverview overview,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    Map<String, ScopeAggregate> aggregates = new LinkedHashMap<>();
    for (AdminDashboardReadModel.JoinAuditRecord record : overview.recentFailures()) {
      addScopeAggregate(aggregates, "room", record.roomId(), record.errorCode(), record.reasonCategory());
      addScopeAggregate(aggregates, "meeting", record.meetingId(), record.errorCode(), record.reasonCategory());
    }
    return aggregates.values().stream()
        .sorted(Comparator.comparingLong(ScopeAggregate::affectedAttempts).reversed())
        .limit(4)
        .map(aggregate -> new AdminDashboardSummaryResponse.AffectedScopeSummary(
            aggregate.scopeType(),
            aggregate.scopeValue(),
            aggregate.affectedAttempts(),
            "%s %s требует проверки: %d свежих отказов за окно %s.".formatted(
                aggregate.scopeType(),
                aggregate.scopeValue(),
                aggregate.affectedAttempts(),
                period),
          handoffFactory.buildHandoffContext(
                environment,
                period,
                aggregate.affectedAttempts() >= 3 ? SEVERITY_CRITICAL : SEVERITY_WARNING,
                aggregate.errorCode(),
                aggregate.category(),
                "room".equals(aggregate.scopeType()) ? aggregate.scopeValue() : roomId,
                "meeting".equals(aggregate.scopeType()) ? aggregate.scopeValue() : meetingId,
                null)))
        .toList();
  }

  private AdminDashboardSummaryResponse.SafeStateSummary buildSafeStateSummary(
      boolean stable,
      List<AdminDashboardSummaryResponse.LatestSpike> latestSpikes,
      String environment,
      String period) {
    String incidentsHref = handoffFactory.buildAdminHref("/admin/incidents", environment, period, null, null, null, null, null);
    List<AdminDashboardSummaryResponse.SafeStateAction> actions = new ArrayList<>();
    actions.add(new AdminDashboardSummaryResponse.SafeStateAction(OPEN_INCIDENTS, incidentsHref));
    actions.add(new AdminDashboardSummaryResponse.SafeStateAction(
        "Открыть историю ролей",
      handoffFactory.buildAdminHref("/admin/role-history", environment, null, null, null, null, null, null)));
    actions.add(new AdminDashboardSummaryResponse.SafeStateAction(
        "Открыть конфиг-наборы",
      handoffFactory.buildAdminHref("/admin/config-sets", environment, null, null, null, null, null, null)));
    if (incidentDetailUrl != null && !incidentDetailUrl.isBlank()) {
      actions.add(new AdminDashboardSummaryResponse.SafeStateAction("Открыть текущий incident detail", incidentDetailUrl));
    }
    List<AdminDashboardSummaryResponse.ResolvedSpikeSummary> resolvedSpikes = stable
        ? latestSpikes.stream()
            .limit(2)
            .map(spike -> new AdminDashboardSummaryResponse.ResolvedSpikeSummary(spike.errorCode(), spike.summary()))
            .toList()
        : List.of();
    return new AdminDashboardSummaryResponse.SafeStateSummary(
        stable,
        stable ? "Система стабильна" : "Есть активные сигналы",
        stable
            ? "Пустого технического состояния нет: используйте incident queue и вторичные модули для контрольной проверки."
            : "Приоритетные сигналы уже собраны выше. Переходите в очередь инцидентов одним действием.",
        List.copyOf(actions),
        resolvedSpikes);
  }

  private void addScopeAggregate(
      Map<String, ScopeAggregate> aggregates,
      String scopeType,
      String scopeValue,
      String errorCode,
      String category) {
    if (scopeValue == null || scopeValue.isBlank()) {
      return;
    }
    String key = scopeType + ":" + scopeValue;
    aggregates.compute(
        key,
        (ignored, current) -> current == null
            ? new ScopeAggregate(scopeType, scopeValue, 1, errorCode, category)
            : new ScopeAggregate(
                current.scopeType(),
                current.scopeValue(),
                current.affectedAttempts() + 1,
                current.errorCode() == null ? errorCode : current.errorCode(),
                current.category() == null ? category : current.category()));
  }

  record SummaryRequest(
      String period,
      String environment,
      String tenantId,
      Instant generatedAt,
      String traceId,
      HealthResponse health,
      JoinReadinessResponse joinReadiness,
      ConfigSet activeConfigSet,
      ConfigSetCompatibilityCheck compatibility,
      AdminDashboardReadModel.JoinAuditOverview overview,
      String roomId,
      String meetingId) {
  }

  private record ScopeAggregate(
      String scopeType,
      String scopeValue,
      long affectedAttempts,
      String errorCode,
      String category) {
  }
}