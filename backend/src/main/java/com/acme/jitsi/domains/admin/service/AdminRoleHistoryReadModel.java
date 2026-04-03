package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public interface AdminRoleHistoryReadModel {

  PageResult loadHistory(Filter filter);

  record Filter(
      String tenantId,
      @Nullable ConfigSetEnvironmentType environmentType,
      @Nullable String query,
      @Nullable String actionType,
      @Nullable String role,
      @Nullable String actorId,
      @Nullable String subjectId,
      @Nullable String roomId,
      @Nullable String meetingId,
      @Nullable String traceId,
      Instant from,
      Instant to,
      int page,
      int pageSize) {
  }

  record PageResult(List<RoleHistoryRow> rows, long totalElements) {
  }

  record RoleHistoryRow(
      Instant occurredAt,
      String actionType,
      @Nullable String oldRole,
      @Nullable String newRole,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      String roomId,
      String meetingId,
      String actorId,
      @Nullable String actorFullName,
      @Nullable String subjectId,
      @Nullable String subjectFullName,
      @Nullable String traceId) {
  }
}