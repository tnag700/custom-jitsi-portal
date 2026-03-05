package com.acme.jitsi.domains.invites.service;

interface InviteUsageStore {

  void assertCanConsume(InviteExchangeProperties.Invite invite);

  void consume(InviteExchangeProperties.Invite invite);

  void rollback(String inviteToken);
}