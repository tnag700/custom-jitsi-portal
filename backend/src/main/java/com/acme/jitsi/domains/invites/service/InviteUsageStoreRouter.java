package com.acme.jitsi.domains.invites.service;

import org.springframework.stereotype.Component;

@Component
class InviteUsageStoreRouter {

  private final InviteUsageStoreResolver inviteUsageStoreResolver;

  InviteUsageStoreRouter(InviteUsageStoreResolver inviteUsageStoreResolver) {
    this.inviteUsageStoreResolver = inviteUsageStoreResolver;
  }

  void consume(InviteExchangeProperties.Invite invite) {
    selectStore().consume(invite);
  }

  void assertCanConsume(InviteExchangeProperties.Invite invite) {
    selectStore().assertCanConsume(invite);
  }

  void rollback(String inviteToken) {
    selectStore().rollback(inviteToken);
  }

  private InviteUsageStore selectStore() {
    return inviteUsageStoreResolver.resolve();
  }
}