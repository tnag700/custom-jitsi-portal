package com.acme.jitsi.domains.meetings.service;

import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(400)
class UnknownRolePolicyMeetingRoleResolutionPolicy implements MeetingRoleResolutionPolicy {

  @Override
  public Optional<MeetingRole> resolve(MeetingRoleResolutionContext context) {
    if (context.properties().unknownRolePolicy() == MeetingTokenProperties.UnknownRolePolicy.DENY_ACCESS) {
      throw new MeetingTokenException(
          HttpStatus.FORBIDDEN,
          "ACCESS_DENIED",
          "Доступ к встрече запрещен: отсутствует допустимый role-claim.");
    }
    return Optional.of(MeetingRole.PARTICIPANT);
  }
}