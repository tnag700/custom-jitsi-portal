package com.acme.jitsi.domains.meetings.service;

import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(300)
class DbParticipantAssignmentMeetingRoleResolutionPolicy implements MeetingRoleResolutionPolicy {

  private final MeetingParticipantAssignmentRepository assignmentRepository;

  DbParticipantAssignmentMeetingRoleResolutionPolicy(MeetingParticipantAssignmentRepository assignmentRepository) {
    this.assignmentRepository = assignmentRepository;
  }

  @Override
  public Optional<MeetingRole> resolve(MeetingRoleResolutionContext context) {
    return assignmentRepository
        .findByMeetingIdAndSubjectId(context.meetingId(), context.subject())
        .map(MeetingParticipantAssignment::role);
  }
}
