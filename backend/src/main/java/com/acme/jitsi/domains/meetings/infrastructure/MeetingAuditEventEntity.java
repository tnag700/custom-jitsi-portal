package com.acme.jitsi.domains.meetings.infrastructure;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "meeting_audit_events")
class MeetingAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "action_type", nullable = false, length = 32)
  private String actionType;

  @Column(name = "room_id", nullable = false, length = 64)
  private String roomId;

  @Column(name = "meeting_id", nullable = false, length = 64)
  private String meetingId;

  @Column(name = "actor_id", nullable = false, length = 255)
  private String actorId;

  @Column(name = "subject_id", length = 255)
  private String subjectId;

  @Column(name = "trace_id", length = 128)
  private String traceId;

  @Column(name = "changed_fields", nullable = false, columnDefinition = "TEXT")
  private String changedFields;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected MeetingAuditEventEntity() {
  }

  MeetingAuditEventEntity(
      String actionType,
      String roomId,
      String meetingId,
      String actorId,
      String traceId,
      String changedFields,
      String subjectId,
      Instant createdAt) {
    this.actionType = actionType;
    this.roomId = roomId;
    this.meetingId = meetingId;
    this.actorId = actorId;
    this.subjectId = subjectId;
    this.traceId = traceId;
    this.changedFields = changedFields;
    this.createdAt = createdAt;
  }
}
