package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(40)
class InviteExpirationValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    Instant expiresAt = context.invite().expiresAt();
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
      throw new MeetingTokenException(HttpStatus.GONE, "INVITE_EXPIRED", "Срок действия инвайта истек.");
    }
  }
}