package com.acme.jitsi.domains.meetings.service;

import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(100)
class BlockedSubjectMeetingRoleResolutionPolicy implements MeetingRoleResolutionPolicy {

  @Override
  public Optional<MeetingRole> resolve(MeetingRoleResolutionContext context) {
    if (context.properties().blockedSubjects().contains(context.subject())) {
      throw new MeetingTokenException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Доступ к встрече запрещен.");
    }
    return Optional.empty();
  }
}