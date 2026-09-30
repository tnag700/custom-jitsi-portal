package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.shared.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class MeetingRoleResolver {

  private final MeetingTokenProperties properties;
  private final MeetingRepository meetingRepository;
  private final MeetingParticipantAssignmentRepository assignmentRepository;

  MeetingRoleResolver(
      MeetingTokenProperties properties,
      MeetingRepository meetingRepository,
      MeetingParticipantAssignmentRepository assignmentRepository) {
    this.properties = properties;
    this.meetingRepository = meetingRepository;
    this.assignmentRepository = assignmentRepository;
  }

  MeetingRole resolve(String meetingId, String subject) {
    if (properties.blockedSubjects().contains(subject)) {
      throw new MeetingTokenException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.code(), "Доступ к встрече запрещен.");
    }

    if (!properties.knownMeetingIds().isEmpty()
        && !properties.knownMeetingIds().contains(meetingId)
        && !meetingRepository.existsById(meetingId)) {
      throw new MeetingTokenException(HttpStatus.NOT_FOUND, ErrorCode.MEETING_NOT_FOUND.code(), "Встреча не найдена.");
    }

    var persistedRole = assignmentRepository.findByMeetingIdAndSubjectId(meetingId, subject)
        .map(MeetingParticipantAssignment::role);
    if (persistedRole.isPresent()) {
      return persistedRole.get();
    }

    var matchingAssignments = properties.assignments().stream()
        .filter(assignment -> assignment.meetingId().equals(meetingId) && assignment.subject().equals(subject))
        .toList();
    if (matchingAssignments.size() > 1) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.ROLE_MISMATCH.code(),
          "Conflicting role assignments for subject '" + subject + "' in meeting '" + meetingId + "'.");
    }
    if (!matchingAssignments.isEmpty()) {
      return MeetingRole.from(matchingAssignments.get(0).role())
          .orElseThrow(() -> new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.ROLE_MISMATCH.code(),
              "Invalid configured role for subject '" + subject + "' in meeting '" + meetingId + "'."));
    }

    if (properties.unknownRolePolicy() == MeetingTokenProperties.UnknownRolePolicy.DENY_ACCESS) {
      throw new MeetingTokenException(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.code(),
          "Доступ к встрече запрещен: отсутствует допустимый role-claim.");
    }
    return MeetingRole.PARTICIPANT;
  }
}
