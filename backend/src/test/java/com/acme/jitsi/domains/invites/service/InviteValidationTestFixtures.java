package com.acme.jitsi.domains.invites.service;

import java.time.Instant;
import java.util.List;
import org.mockito.Mockito;

final class InviteValidationTestFixtures {

  private InviteValidationTestFixtures() {
  }

  static InviteExchangeProperties.Invite invite(
      String token,
      String meetingId,
      Instant expiresAt,
      boolean revoked,
      int usageLimit) {
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken(token);
    invite.setMeetingId(meetingId);
    invite.setExpiresAt(expiresAt);
    invite.setRevoked(revoked);
    invite.setUsageLimit(usageLimit);
    return invite;
  }

  static InviteExchangeProperties propertiesWithInvites(List<InviteExchangeProperties.Invite> invites) {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    properties.setInvites(invites);
    return properties;
  }

  static InviteValidationContext context(String token, InviteExchangeProperties properties) {
    return new InviteValidationContext(token, properties, Mockito.mock(InviteUsageStoreRouter.class));
  }
}