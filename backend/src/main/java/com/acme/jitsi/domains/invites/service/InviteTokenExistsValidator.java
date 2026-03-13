package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(20)
class InviteTokenExistsValidator implements InviteValidator {

  @Override
  public boolean requiresResolvedInvite() {
    return false;
  }

  @Override
  public boolean loadsResolvedInvite() {
    return true;
  }

  @Override
  public void validate(InviteValidationContext context) {
    InviteExchangeProperties.Invite invite = context.properties().invites().stream()
        .filter(candidate -> context.inviteToken().equals(candidate.token()))
        .findFirst()
         .orElseThrow(() -> new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code(), "Инвайт не найден."));
    context.setInvite(invite);
  }
}