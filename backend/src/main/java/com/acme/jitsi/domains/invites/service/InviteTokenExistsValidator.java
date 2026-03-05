package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(20)
class InviteTokenExistsValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    InviteExchangeProperties.Invite invite = context.properties().invites().stream()
        .filter(candidate -> context.inviteToken().equals(candidate.token()))
        .findFirst()
        .orElseThrow(() -> new MeetingTokenException(HttpStatus.NOT_FOUND, "INVALID_INVITE", "Инвайт недействителен."));
    context.setInvite(invite);
  }
}