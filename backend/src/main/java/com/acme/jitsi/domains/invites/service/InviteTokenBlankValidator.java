package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(10)
class InviteTokenBlankValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    String inviteToken = context.inviteToken();
    if (inviteToken == null || inviteToken.isBlank()) {
      throw new MeetingTokenException(HttpStatus.NOT_FOUND, "INVALID_INVITE", "Инвайт недействителен.");
    }
  }
}