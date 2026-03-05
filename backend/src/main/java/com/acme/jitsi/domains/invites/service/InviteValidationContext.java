package com.acme.jitsi.domains.invites.service;

final class InviteValidationContext {

  private final String inviteToken;
  private final InviteExchangeProperties properties;
  private final InviteUsageStoreRouter inviteUsageStoreRouter;
  private InviteExchangeProperties.Invite invite;

  InviteValidationContext(
      String inviteToken,
      InviteExchangeProperties properties,
      InviteUsageStoreRouter inviteUsageStoreRouter) {
    this.inviteToken = inviteToken;
    this.properties = properties;
    this.inviteUsageStoreRouter = inviteUsageStoreRouter;
  }

  String inviteToken() {
    return inviteToken;
  }

  InviteExchangeProperties properties() {
    return properties;
  }

  InviteUsageStoreRouter inviteUsageStoreRouter() {
    return inviteUsageStoreRouter;
  }

  InviteExchangeProperties.Invite invite() {
    return invite;
  }

  void setInvite(InviteExchangeProperties.Invite invite) {
    this.invite = invite;
  }
}