package com.acme.jitsi.domains.meetings.service;

public interface MeetingAuditLog {

  default void record(
      String actionType,
      String roomId,
      String meetingId,
      String actorId,
      String traceId,
      String changedFields) {
    record(actionType, roomId, meetingId, actorId, traceId, changedFields, null);
  }

  void record(
      String actionType,
      String roomId,
      String meetingId,
      String actorId,
      String traceId,
      String changedFields,
      String subjectId);
}
