package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import java.util.ArrayList;
import java.util.List;

final class AdminDashboardStatusBuilder {

  private static final String CATEGORY_CONFIG = "CONFIG";
  private static final String SEVERITY_INFO = "info";
  private static final String SEVERITY_WARNING = "warning";
  private static final String SEVERITY_CRITICAL = "critical";

  private final AdminDashboardHandoffFactory handoffFactory;
  private final AdminDashboardSummarySignalSupport signalSupport;

  AdminDashboardStatusBuilder(
      AdminDashboardHandoffFactory handoffFactory,
      AdminDashboardSummarySignalSupport signalSupport) {
    this.handoffFactory = handoffFactory;
    this.signalSupport = signalSupport;
  }

  List<AdminDashboardSummaryResponse.ServiceStatus> buildServiceStatuses(
      HealthResponse health,
      JoinReadinessResponse joinReadiness,
      ConfigSet activeConfigSet,
      ConfigSetCompatibilityCheck compatibility,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    List<AdminDashboardSummaryResponse.ServiceStatus> statuses = new ArrayList<>();
    statuses.add(buildPortalStatus(joinReadiness, environment, period, roomId, meetingId));
    statuses.add(buildBackendApiStatus(health, compatibility, environment, period, roomId, meetingId));
    statuses.add(buildMeetingJoinSurfaceStatus(joinReadiness, environment, period, roomId, meetingId));
    addActiveConfigSetStatus(statuses, activeConfigSet, compatibility, environment, period, roomId, meetingId);
    return List.copyOf(statuses);
  }

  private AdminDashboardSummaryResponse.ServiceStatus buildPortalStatus(
      JoinReadinessResponse joinReadiness,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    boolean published = joinReadiness.publicJoinUrl() != null && !joinReadiness.publicJoinUrl().isBlank();
    return new AdminDashboardSummaryResponse.ServiceStatus(
        "portal",
        "Portal",
        published ? "UP" : "DEGRADED",
        published
            ? "Портал публикует ссылку входа и server-side filters для оператора."
            : "Публичная join surface не опубликована в readiness snapshot.",
        handoffFactory.buildHandoffContext(
            environment,
            period,
            SEVERITY_WARNING,
            null,
            null,
            roomId,
            meetingId,
            null));
  }

  private AdminDashboardSummaryResponse.ServiceStatus buildBackendApiStatus(
      HealthResponse health,
      ConfigSetCompatibilityCheck compatibility,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    return new AdminDashboardSummaryResponse.ServiceStatus(
        "backend-api",
        "Backend API",
        health.status(),
        health.details() == null || health.details().isBlank()
            ? "Health surface не сигнализирует проблем совместимости."
            : health.details(),
        handoffFactory.buildHandoffContext(
            environment,
            period,
            signalSupport.severityForHealth(health),
            null,
            compatibility != null && !compatibility.compatible() ? CATEGORY_CONFIG : null,
            roomId,
            meetingId,
            null));
  }

  private AdminDashboardSummaryResponse.ServiceStatus buildMeetingJoinSurfaceStatus(
      JoinReadinessResponse joinReadiness,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    String joinStatus = joinReadiness.status();
    return new AdminDashboardSummaryResponse.ServiceStatus(
        "meeting-join-surface",
        "Meeting / Join Surface",
        mapJoinStatus(joinStatus),
        switch (joinStatus) {
          case "blocked" -> "Join readiness сообщает блокирующие проверки. Вход во встречи деградирован.";
          case "degraded" -> "Join readiness сообщает предупреждения. Вход возможен с рисками.";
          default -> "Join readiness сообщает готовность к входу во встречи.";
        },
        handoffFactory.buildHandoffContext(
            environment,
            period,
            signalSupport.severityForJoinStatus(joinStatus),
            signalSupport.primaryJoinErrorCode(joinReadiness.systemChecks()),
            null,
            roomId,
            meetingId,
            null));
  }

  private void addActiveConfigSetStatus(
      List<AdminDashboardSummaryResponse.ServiceStatus> statuses,
      ConfigSet activeConfigSet,
      ConfigSetCompatibilityCheck compatibility,
      String environment,
      String period,
      String roomId,
      String meetingId) {
    if (activeConfigSet == null) {
      return;
    }
    boolean incompatible = compatibility != null && !compatibility.compatible();
    statuses.add(new AdminDashboardSummaryResponse.ServiceStatus(
        "active-config-set",
        "Active Config Set",
        incompatible ? "DEGRADED" : "UP",
        "Активный config set %s подключён для выбранного окружения.".formatted(activeConfigSet.configSetId()),
        handoffFactory.buildHandoffContext(
            environment,
            period,
            incompatible ? SEVERITY_CRITICAL : SEVERITY_INFO,
            signalSupport.configErrorCode(incompatible),
            CATEGORY_CONFIG,
            roomId,
            meetingId,
            null)));
  }

  private String mapJoinStatus(String joinStatus) {
    return switch (joinStatus) {
      case "blocked" -> "DOWN";
      case "degraded" -> "DEGRADED";
      default -> "UP";
    };
  }
}
