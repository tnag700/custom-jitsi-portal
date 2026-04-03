package com.acme.jitsi.domains.admin.dto;

import java.util.List;

public record AdminDashboardDrillDownResponse(
    String period,
    String environment,
    String tenantId,
    String generatedAt,
    String selectionType,
    String selectionValue,
    EntityFilter entityFilter,
    long failureCount,
    List<RecentSample> recentSamples,
    boolean sampleWindowLimited) {

  public record EntityFilter(String roomId, String meetingId) {
  }

  public record RecentSample(
      String occurredAt,
      String roomId,
      String meetingId,
      String subjectId,
      String traceId,
      String traceUrl,
      String errorCode,
      String reasonCategory,
      String userMessage) {
  }
}