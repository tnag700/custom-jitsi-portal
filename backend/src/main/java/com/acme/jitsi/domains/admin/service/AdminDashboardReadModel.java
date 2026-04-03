package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Instant;
import java.util.List;

public interface AdminDashboardReadModel {

  JoinAuditOverview loadJoinAuditOverview(DashboardFilter filter);

  DrillDownOverview loadDrillDown(DrillDownFilter filter);

  record DashboardFilter(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      Instant from,
      String roomId,
      String meetingId,
      int sampleLimit) {
  }

  record DrillDownFilter(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      Instant from,
      String roomId,
      String meetingId,
      String errorCode,
      String reasonCategory,
      int sampleLimit) {
  }

  record JoinAuditOverview(
      long successCount,
      long failureCount,
      List<Count> topCategories,
      List<Count> topErrorCodes,
      List<JoinAuditRecord> recentFailures,
      boolean sampleWindowLimited) {
  }

  record DrillDownOverview(
      String selectionType,
      String selectionValue,
      long failureCount,
      List<JoinAuditRecord> recentFailures,
      boolean sampleWindowLimited) {
  }

  record Count(String key, long count) {
  }

  record JoinAuditRecord(
      Instant occurredAt,
      String roomId,
      String meetingId,
      String subjectId,
      String traceId,
      String errorCode,
      String reasonCategory,
      String userMessage) {
  }
}