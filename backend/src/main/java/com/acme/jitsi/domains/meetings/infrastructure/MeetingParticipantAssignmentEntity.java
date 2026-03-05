package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignment;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "meeting_participant_assignments")
public class MeetingParticipantAssignmentEntity {

  @Id
  @Column(name = "assignment_id", nullable = false, updatable = false)
  private String assignmentId;

  @Column(name = "meeting_id", nullable = false)
  private String meetingId;

  @Column(name = "subject_id", nullable = false)
  private String subjectId;

  @Convert(converter = MeetingRoleConverter.class)
  @Column(name = "role", nullable = false)
  private MeetingRole role;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Column(name = "assigned_by", nullable = false)
  private String assignedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected MeetingParticipantAssignmentEntity() {
  }

  MeetingParticipantAssignmentEntity(MeetingParticipantAssignment assignment) {
    this.assignmentId = assignment.assignmentId();
    this.meetingId = assignment.meetingId();
    this.subjectId = assignment.subjectId();
    this.role = assignment.role();
    this.assignedAt = assignment.assignedAt();
    this.assignedBy = assignment.assignedBy();
    this.createdAt = assignment.createdAt();
    this.updatedAt = assignment.updatedAt();
  }

  MeetingParticipantAssignment toDomain() {
    return MeetingParticipantAssignment.builder()
        .assignmentId(assignmentId)
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(role)
        .assignedAt(assignedAt)
        .assignedBy(assignedBy)
        .createdAt(createdAt)
        .updatedAt(updatedAt)
        .build();
  }

  void updateFrom(MeetingParticipantAssignment assignment) {
    this.role = assignment.role();
    this.assignedAt = assignment.assignedAt();
    this.assignedBy = assignment.assignedBy();
    this.updatedAt = assignment.updatedAt();
  }
}
