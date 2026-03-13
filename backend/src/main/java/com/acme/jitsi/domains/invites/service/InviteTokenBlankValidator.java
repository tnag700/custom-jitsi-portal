package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(10)
class InviteTokenBlankValidator implements InviteValidator {

  @Override
  public boolean requiresResolvedInvite() {
    return false;
  }

  @Override
  public void validate(InviteValidationContext context) {
    String inviteToken = context.inviteToken();
    if (inviteToken == null || inviteToken.isBlank()) {
      throw new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code(), "Инвайт не найден.");
    }
  }
}