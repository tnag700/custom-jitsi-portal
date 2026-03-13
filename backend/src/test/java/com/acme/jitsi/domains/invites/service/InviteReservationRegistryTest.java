package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InviteReservationRegistryTest {

  @Test
  void rollbackAuthorizationIsSingleUse() {
    InviteReservationRegistry registry = new InviteReservationRegistry();

    InviteReservation reservation = registry.issue("invite-a", "meeting-a");

    assertThat(registry.authorizeRollback(reservation)).isTrue();
    assertThat(registry.authorizeRollback(reservation)).isFalse();
  }

  @Test
  void rollbackAuthorizationRejectsForgedReservation() {
    InviteReservationRegistry registry = new InviteReservationRegistry();
    registry.issue("invite-a", "meeting-a");

    InviteReservation forgedReservation = InviteReservation.issue("forged", "invite-a", "meeting-a");

    assertThat(registry.authorizeRollback(forgedReservation)).isFalse();
  }
}