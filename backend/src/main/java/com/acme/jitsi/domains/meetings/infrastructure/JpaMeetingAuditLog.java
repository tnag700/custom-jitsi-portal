package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingAuditLog;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class JpaMeetingAuditLog implements MeetingAuditLog {

  private final MeetingAuditEventJpaRepository repository;

  JpaMeetingAuditLog(MeetingAuditEventJpaRepository repository) {
    this.repository = repository;
  }

  @Override
  public void record(
      String actionType,
      String roomId,
      String meetingId,
      String actorId,
      String traceId,
      String changedFields,
      String subjectId) {
    repository.save(new MeetingAuditEventEntity(
        actionType,
        roomId,
        meetingId,
        actorId,
        traceId,
        changedFields,
        subjectId,
        Instant.now()));
  }
}
