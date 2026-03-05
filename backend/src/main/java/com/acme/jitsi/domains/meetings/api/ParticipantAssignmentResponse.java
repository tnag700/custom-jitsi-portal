package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignment;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

record ParticipantAssignmentResponse(
    String assignmentId,
    String meetingId,
    String subjectId,
    String role,
    Instant assignedAt,
    String assignedBy,
    Instant createdAt,
    Instant updatedAt,
    @Nullable String fullName,
    @Nullable String organization,
    @Nullable String position) {

  static ParticipantAssignmentResponse fromDomain(MeetingParticipantAssignment assignment) {
    return new ParticipantAssignmentResponse(
        assignment.assignmentId(),
        assignment.meetingId(),
        assignment.subjectId(),
        assignment.role().value,
        assignment.assignedAt(),
        assignment.assignedBy(),
        assignment.createdAt(),
        assignment.updatedAt(),
        null,
        null,
        null
    );
  }

  static ParticipantAssignmentResponse fromDomainWithProfile(
      MeetingParticipantAssignment assignment,
      @Nullable String fullName,
      @Nullable String organization,
      @Nullable String position) {
    return new ParticipantAssignmentResponse(
        assignment.assignmentId(),
        assignment.meetingId(),
        assignment.subjectId(),
        assignment.role().value,
        assignment.assignedAt(),
        assignment.assignedBy(),
        assignment.createdAt(),
        assignment.updatedAt(),
        fullName,
        organization,
        position
    );
  }
}
