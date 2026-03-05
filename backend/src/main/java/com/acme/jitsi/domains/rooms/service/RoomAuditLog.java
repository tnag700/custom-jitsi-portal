package com.acme.jitsi.domains.rooms.service;

public interface RoomAuditLog {

  void record(
      String actionType,
      String roomId,
      String actorId,
      String traceId,
      String changedFields,
      String oldValues,
      String newValues);
}