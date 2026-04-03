package com.acme.jitsi.domains.admin.dto;

import java.util.List;

public record AdminRoleHistoryResponse(
    String tenantId,
    String environment,
    String generatedAt,
    int page,
    int pageSize,
    long totalElements,
    int totalPages,
    List<RoleHistoryEntry> content) {

  public record RoleHistoryEntry(
      String occurredAt,
      String actionType,
      String actionLabel,
      String oldRole,
      String newRole,
      String subjectLabel,
      String subjectReference,
      String actorLabel,
      String actorReference,
      String tenantId,
      String environment,
      String roomId,
      String meetingId,
      String traceId) {
  }
}