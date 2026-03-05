package com.acme.jitsi.domains.configsets.service;

public interface ConfigSetAuditLog {
  void record(
      String eventType,
      String configSetId,
      String actorId,
      String traceId,
      String changedFields,
      String oldValues,
      String newValues);
}