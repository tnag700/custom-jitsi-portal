package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class AdminDashboardDegradationBuilder {

  private static final String CATEGORY_CONFIG = "CONFIG";
  private static final String OPEN_INCIDENTS = "Открыть очередь инцидентов";
  private static final String SEVERITY_WARNING = "warning";
  private static final String SEVERITY_CRITICAL = "critical";

  private final AdminDashboardHandoffFactory handoffFactory;
  private final AdminDashboardSummarySignalSupport signalSupport;

  AdminDashboardDegradationBuilder(
      AdminDashboardHandoffFactory handoffFactory,
      AdminDashboardSummarySignalSupport signalSupport) {
    this.handoffFactory = handoffFactory;
    this.signalSupport = signalSupport;
  }

  List<AdminDashboardSummaryResponse.DegradationSummary> buildTopDegradations(
      HealthResponse health,
      JoinReadinessResponse joinReadiness,
      ConfigSet activeConfigSet,
      ConfigSetCompatibilityCheck compatibility,
      AdminDashboardReadModel.JoinAuditOverview overview,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    List<AdminDashboardSummaryResponse.DegradationSummary> degradations = new ArrayList<>();
    addHealthDegradation(degradations, health, environment, period, roomId, meetingId);
    addJoinDegradation(degradations, joinReadiness, environment, period, roomId, meetingId);
    addConfigDegradation(degradations, activeConfigSet, compatibility, environment, period, roomId, meetingId);
    addFailureSpikeDegradation(degradations, overview, environment, period, roomId, meetingId);

    return degradations.stream()
        .sorted(Comparator
            .comparingInt((AdminDashboardSummaryResponse.DegradationSummary item) -> signalSupport.severityRank(item.severity()))
            .thenComparingInt(item -> signalSupport.degradationPriority(item.id())))
        .limit(4)
        .toList();
  }

  private void addHealthDegradation(
      List<AdminDashboardSummaryResponse.DegradationSummary> degradations,
      HealthResponse health,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    if ("UP".equalsIgnoreCase(health.status())) {
      return;
    }
    String severity = signalSupport.severityForHealth(health);
    degradations.add(new AdminDashboardSummaryResponse.DegradationSummary(
        "backend-api",
        "Backend API требует внимания",
        health.details() == null || health.details().isBlank()
            ? "Health surface сообщает деградацию ключевого сервиса."
            : health.details(),
        severity,
        OPEN_INCIDENTS,
        handoffFactory.buildHandoffContext(environment, period, severity, null, "HEALTH", roomId, meetingId, null)));
  }

  private void addJoinDegradation(
      List<AdminDashboardSummaryResponse.DegradationSummary> degradations,
      JoinReadinessResponse joinReadiness,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    if ("ready".equalsIgnoreCase(joinReadiness.status())) {
      return;
    }
    String severity = signalSupport.severityForJoinStatus(joinReadiness.status());
    degradations.add(new AdminDashboardSummaryResponse.DegradationSummary(
        "join-readiness",
        "Join surface требует triage",
        signalSupport.primaryJoinSummary(joinReadiness),
        severity,
        OPEN_INCIDENTS,
        handoffFactory.buildHandoffContext(
            environment,
            period,
            severity,
            signalSupport.primaryJoinErrorCode(joinReadiness.systemChecks()),
            "JOIN",
            roomId,
            meetingId,
            null)));
  }

  private void addConfigDegradation(
      List<AdminDashboardSummaryResponse.DegradationSummary> degradations,
      ConfigSet activeConfigSet,
      ConfigSetCompatibilityCheck compatibility,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    if (activeConfigSet == null || compatibility != null && compatibility.compatible()) {
      return;
    }
    String severity = compatibility == null ? SEVERITY_WARNING : SEVERITY_CRITICAL;
    String errorCode = signalSupport.configErrorCode(compatibility != null && !compatibility.compatible());
    degradations.add(new AdminDashboardSummaryResponse.DegradationSummary(
        "config-compatibility",
        "Config compatibility требует немедленного внимания",
        signalSupport.buildCompatibilitySummary(activeConfigSet, compatibility),
        severity,
        OPEN_INCIDENTS,
        handoffFactory.buildHandoffContext(
            environment,
            period,
            severity,
            errorCode,
            CATEGORY_CONFIG,
            roomId,
            meetingId,
            null)));
  }

  private void addFailureSpikeDegradation(
      List<AdminDashboardSummaryResponse.DegradationSummary> degradations,
      AdminDashboardReadModel.JoinAuditOverview overview,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    if (overview.failureCount() <= 0) {
      return;
    }
    String topErrorCode = overview.topErrorCodes().isEmpty() ? null : overview.topErrorCodes().get(0).key();
    String category = signalSupport.findCategoryForErrorCode(overview, topErrorCode);
    String severity = overview.failureCount() >= 3 ? SEVERITY_CRITICAL : SEVERITY_WARNING;
    degradations.add(new AdminDashboardSummaryResponse.DegradationSummary(
        "failure-spike",
        "Свежий всплеск отказов требует triage",
        topErrorCode == null
            ? "За выбранное окно есть свежие отказы входа, требующие расследования в incident queue."
            : "Код %s доминирует среди свежих отказов за выбранное окно.".formatted(topErrorCode),
        severity,
        OPEN_INCIDENTS,
        handoffFactory.buildHandoffContext(
            environment,
            period,
            severity,
            topErrorCode,
            category,
            roomId,
            meetingId,
            null)));
  }
}