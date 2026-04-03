package com.acme.jitsi.domains.admin.dto;

import java.util.List;

public record AdminDashboardSummaryResponse(
    String period,
    String environment,
    String tenantId,
    String generatedAt,
    String traceId,
    PriorityBanner priorityBanner,
    List<DegradationSummary> topDegradations,
    List<ServiceStatus> keyServiceStatuses,
    List<LatestSpike> latestSpikes,
    List<AffectedScopeSummary> affectedScopeSummary,
    SafeStateSummary safeStateSummary,
    EntityFilter entityFilter,
    boolean sampleWindowLimited) {

  public record PriorityBanner(
      boolean active,
      String severity,
      String headline,
      String summary,
      String actionLabel,
      HandoffContext handoff) {
  }

  public record DegradationSummary(
      String id,
      String title,
      String summary,
      String severity,
      String actionLabel,
      HandoffContext handoff) {
  }

  public record ServiceStatus(
      String key,
      String label,
      String status,
      String detail,
      HandoffContext handoff) {
  }

  public record LatestSpike(
      String errorCode,
      String category,
      long count,
      String summary,
      HandoffContext handoff) {
  }

  public record AffectedScopeSummary(
      String scopeType,
      String scopeValue,
      long affectedAttempts,
      String summary,
      HandoffContext handoff) {
  }

  public record SafeStateSummary(
      boolean stable,
      String headline,
      String summary,
      List<SafeStateAction> actions,
      List<ResolvedSpikeSummary> recentResolvedSpikes) {
  }

  public record SafeStateAction(String label, String href) {
  }

  public record ResolvedSpikeSummary(String label, String detail) {
  }

  public record HandoffContext(
      String environment,
      String period,
      String severity,
      String errorCode,
      String category,
      String roomId,
      String meetingId,
      String incidentId) {
  }

  public record EntityFilter(String roomId, String meetingId) {
  }
}