package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(30)
class InviteRevokedValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    if (context.invite().revoked()) {
      throw new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_REVOKED.code(), "Инвайт отозван.");
    }
  }
}