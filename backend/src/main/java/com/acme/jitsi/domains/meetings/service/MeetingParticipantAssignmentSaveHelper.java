package com.acme.jitsi.domains.meetings.service;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
final class MeetingParticipantAssignmentSaveHelper {

  private final MeetingParticipantAssignmentRepository assignmentRepository;
  private final MeetingParticipantAssignmentFactory assignmentFactory;

  MeetingParticipantAssignmentSaveHelper(
      MeetingParticipantAssignmentRepository assignmentRepository,
      MeetingParticipantAssignmentFactory assignmentFactory) {
    this.assignmentRepository = assignmentRepository;
    this.assignmentFactory = assignmentFactory;
  }

  SaveOutcome save(
      MeetingParticipantAssignment assignment,
      Optional<MeetingParticipantAssignment> existing,
      String meetingId,
      String subjectId,
      MeetingRole role,
      String assignedBy) {
    try {
      return new SaveOutcome(assignmentRepository.save(assignment), existing);
    } catch (DataIntegrityViolationException exception) {
      if (isSingleHostConstraintViolation(exception, role)) {
        throw new MeetingRoleConflictException(
            "Meeting '" + meetingId + "' already has a host. Only one host is allowed per meeting.",
            "ROLE_CONFLICT",
            exception);
      }
      return saveAfterConcurrentCreate(
          existing, meetingId, subjectId, role, assignedBy, exception);
    }
  }

  private SaveOutcome saveAfterConcurrentCreate(
      Optional<MeetingParticipantAssignment> existing,
      String meetingId,
      String subjectId,
      MeetingRole role,
      String assignedBy,
      DataIntegrityViolationException originalError) {
    if (existing.isPresent()) {
      throw originalError;
    }

    Optional<MeetingParticipantAssignment> concurrentExisting =
        assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subjectId);
    if (concurrentExisting.isEmpty()) {
      throw originalError;
    }

    MeetingParticipantAssignment retryAssignment =
        assignmentFactory.updatedAssignment(concurrentExisting.get(), role, assignedBy);
    return new SaveOutcome(assignmentRepository.save(retryAssignment), concurrentExisting);
  }

  private boolean isSingleHostConstraintViolation(
      DataIntegrityViolationException exception, MeetingRole role) {
    if (role != MeetingRole.HOST) {
      return false;
    }

    Throwable current = exception;
    while (current != null) {
      if (isConstraintNameMatch(current)
          || isSqlStateUniqueViolation(current)
          || containsHostConstraintMarker(current)) {
        return true;
      }
      current = current.getCause();
    }

    return false;
  }

  private boolean isConstraintNameMatch(Throwable throwable) {
    if (!(throwable instanceof ConstraintViolationException constraintViolationException)) {
      return false;
    }

    String constraintName = constraintViolationException.getConstraintName();
    return constraintName != null && constraintName.equalsIgnoreCase("uk_meeting_single_host");
  }

  private boolean isSqlStateUniqueViolation(Throwable throwable) {
    if (!(throwable instanceof SQLException sqlException)) {
      return false;
    }
    return "23505".equals(sqlException.getSQLState());
  }

  private boolean containsHostConstraintMarker(Throwable throwable) {
    String message = throwable.getMessage();
    return message != null && message.toLowerCase(Locale.ROOT).contains("uk_meeting_single_host");
  }

  record SaveOutcome(
      MeetingParticipantAssignment saved,
      Optional<MeetingParticipantAssignment> existing) {}
}