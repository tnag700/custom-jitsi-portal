package com.acme.jitsi.domains.meetings.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class MeetingParticipantAssignmentFactory {

  private final Clock clock;

  MeetingParticipantAssignmentFactory(Clock clock) {
    this.clock = clock;
  }

  MeetingRole resolveRole(String roleValue) {
    return MeetingRole.from(roleValue)
        .orElseThrow(
            () ->
                new MeetingInvalidRoleException(
                    "Role '" + roleValue + "' is not supported. Allowed values: host, moderator, participant"));
  }

  MeetingParticipantAssignment newAssignment(
      String meetingId, String subjectId, MeetingRole role, String assignedBy) {
    Instant now = Instant.now(clock);
    return MeetingParticipantAssignment.builder()
        .assignmentId(UUID.randomUUID().toString())
        .meetingId(meetingId)
        .subjectId(subjectId)
        .role(role)
        .assignedAt(now)
        .assignedBy(assignedBy)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }

  MeetingParticipantAssignment updatedAssignment(
      MeetingParticipantAssignment existing, MeetingRole role, String assignedBy) {
    Instant now = Instant.now(clock);
    return MeetingParticipantAssignment.builder()
        .assignmentId(existing.assignmentId())
        .meetingId(existing.meetingId())
        .subjectId(existing.subjectId())
        .role(role)
        .assignedAt(now)
        .assignedBy(assignedBy)
        .createdAt(existing.createdAt())
        .updatedAt(now)
        .build();
  }

  String upsertAuditDetail(
      MeetingParticipantAssignment existing, String subjectId, MeetingRole newRole) {
    return existing == null
        ? "subjectId:" + subjectId + ";role:none->" + newRole.value()
        : "subjectId:" + subjectId + ";role:" + existing.role().value() + "->" + newRole.value();
  }

  String removalAuditDetail(MeetingParticipantAssignment existing, String subjectId) {
    return "subjectId:" + subjectId + ";role:" + existing.role().value() + "->none";
  }
}