package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(30)
class InviteRevokedValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    if (context.invite().revoked()) {
      throw new MeetingTokenException(HttpStatus.GONE, "INVITE_REVOKED", "Инвайт отозван.");
    }
  }
}