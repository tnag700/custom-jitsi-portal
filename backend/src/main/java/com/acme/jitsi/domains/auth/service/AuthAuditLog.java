package com.acme.jitsi.domains.auth.service;

public interface AuthAuditLog {

  void record(
      String eventType,
      String actorId,
      String subjectId,
      String meetingId,
      String tokenId,
      String errorCode,
      String traceId,
      String tenantId,
      String clientContext);
}