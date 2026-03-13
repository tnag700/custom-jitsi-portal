package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.shared.ErrorCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class MeetingParticipantBulkAssignmentValidator {

  private final MeetingParticipantAssignmentRepository assignmentRepository;

  MeetingParticipantBulkAssignmentValidator(
      MeetingParticipantAssignmentRepository assignmentRepository) {
    this.assignmentRepository = assignmentRepository;
  }

  void validate(
      Meeting meeting, List<MeetingParticipantAssignmentService.BulkParticipantEntry> entries) {
    validateDuplicateSubjects(entries);

    long hostCount =
        entries.stream().filter(entry -> MeetingRole.HOST.value().equalsIgnoreCase(entry.role())).count();

    if (hostCount > 1) {
      throw new BulkAssignmentValidationException(
          "Multiple hosts specified in bulk assignment. Only one host is allowed per meeting.",
          -1,
          null,
          ErrorCode.MEETING_ROLE_CONFLICT.code(),
          null);
    }

    if (hostCount == 1) {
      validateExistingHostConstraint(meeting, entries);
    }
  }

  private void validateDuplicateSubjects(
      List<MeetingParticipantAssignmentService.BulkParticipantEntry> entries) {
    Map<String, Integer> firstSeenIndexBySubject = new HashMap<>();
    for (int index = 0; index < entries.size(); index++) {
      MeetingParticipantAssignmentService.BulkParticipantEntry entry = entries.get(index);
      Integer firstSeenIndex = firstSeenIndexBySubject.putIfAbsent(entry.subjectId(), index);
      if (firstSeenIndex != null) {
        throw new BulkAssignmentValidationException(
            "Duplicate subjectId '"
                + entry.subjectId()
                + "' in bulk assignment payload. Each participant must appear only once.",
            index,
            entry.subjectId(),
            ErrorCode.INVALID_REQUEST.code(),
            null);
      }
    }
  }

  BulkAssignmentValidationException toBulkValidationException(
      int index,
      MeetingParticipantAssignmentService.BulkParticipantEntry entry,
      RuntimeException exception) {
    String errorCode;
    if (exception instanceof MeetingInvalidRoleException invalid) {
      errorCode = invalid.errorCode();
    } else if (exception instanceof MeetingRoleConflictException conflict) {
      errorCode = conflict.errorCode();
    } else {
      throw new IllegalArgumentException(
          "Unexpected exception type: " + exception.getClass().getName(), exception);
    }

    return new BulkAssignmentValidationException(
        "Failed to assign participant at index " + index + ": " + exception.getMessage(),
        index,
        entry.subjectId(),
        errorCode,
        exception);
  }

  private void validateExistingHostConstraint(
      Meeting meeting, List<MeetingParticipantAssignmentService.BulkParticipantEntry> entries) {
    List<MeetingParticipantAssignment> existingAssignments =
        assignmentRepository.findByMeetingId(meeting.meetingId());
    boolean hasExistingHost =
        existingAssignments.stream().anyMatch(assignment -> assignment.role() == MeetingRole.HOST);
    if (!hasExistingHost) {
      return;
    }

    String newHostSubjectId =
        entries.stream()
            .filter(entry -> MeetingRole.HOST.value().equalsIgnoreCase(entry.role()))
            .findFirst()
            .map(MeetingParticipantAssignmentService.BulkParticipantEntry::subjectId)
            .orElse(null);

    boolean isSameHost =
        existingAssignments.stream()
            .anyMatch(
                assignment ->
                    assignment.role() == MeetingRole.HOST
                        && assignment.subjectId().equals(newHostSubjectId));

    if (!isSameHost) {
      throw new BulkAssignmentValidationException(
          "Meeting '"
              + meeting.meetingId()
              + "' already has a host. Only one host is allowed per meeting.",
          -1,
          null,
          ErrorCode.MEETING_ROLE_CONFLICT.code(),
          null);
    }
  }
}