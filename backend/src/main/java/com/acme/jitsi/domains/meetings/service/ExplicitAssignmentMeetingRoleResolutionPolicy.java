package com.acme.jitsi.domains.meetings.service;

import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(350)
class ExplicitAssignmentMeetingRoleResolutionPolicy implements MeetingRoleResolutionPolicy {

  @Override
  public Optional<MeetingRole> resolve(MeetingRoleResolutionContext context) {
    List<MeetingTokenProperties.RoleAssignment> matchingAssignments = context.properties().assignments().stream()
        .filter(a -> a.meetingId().equals(context.meetingId()) && a.subject().equals(context.subject()))
        .toList();

    if (matchingAssignments.isEmpty()) {
      return Optional.empty();
    }

    if (matchingAssignments.size() > 1) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, "ROLE_MISMATCH",
          "Conflicting role assignments for subject '" + context.subject() + "' in meeting '" + context.meetingId() + "'.");
    }

    Optional<MeetingRole> role = MeetingRole.from(matchingAssignments.get(0).role());
    if (role.isEmpty()) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, "ROLE_MISMATCH",
          "Invalid configured role for subject '" + context.subject() + "' in meeting '" + context.meetingId() + "'.");
    }
    return role;
  }
}