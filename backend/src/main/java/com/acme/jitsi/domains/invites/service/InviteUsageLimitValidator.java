package com.acme.jitsi.domains.invites.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(70)
class InviteUsageLimitValidator implements InviteValidator {

  @Override
  public void validate(InviteValidationContext context) {
    context.inviteUsageStoreRouter().assertCanConsume(context.invite());
  }
}