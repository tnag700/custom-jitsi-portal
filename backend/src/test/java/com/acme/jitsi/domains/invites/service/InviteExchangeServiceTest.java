package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InviteExchangeServiceTest {

  private FakeInviteValidationCapability inviteValidationCapability;
  private FakeInviteReservationCapability inviteReservationCapability;
  private InviteJoinPort inviteJoinPort;
  private InviteExchangeService service;

  @BeforeEach
  void setUp() {
    inviteValidationCapability = new FakeInviteValidationCapability();
    inviteReservationCapability = new FakeInviteReservationCapability();
    inviteJoinPort = org.mockito.Mockito.mock(InviteJoinPort.class);
    service = new InviteExchangeService(inviteValidationCapability, inviteReservationCapability, inviteJoinPort);
  }

  @Test
  void validateUsesValidationCapabilityOnly() {
    inviteValidationCapability.result = new InviteValidationResult("meeting-a");

    InviteExchangeService.ValidationResult result = service.validate("invite-token");

    assertThat(result.meetingId()).isEqualTo("meeting-a");
    assertThat(inviteValidationCapability.lastValidatedToken).isEqualTo("invite-token");
    assertThat(inviteReservationCapability.lastReservedToken).isNull();
    verifyNoInteractions(inviteJoinPort);
  }

  @Test
  void exchangeRollsBackReservationWhenGuestTokenIssuanceFails() {
    InviteReservation reservation = InviteReservation.issue("reservation-1", "invite-token", "meeting-a");
    inviteReservationCapability.reservation = reservation;
    when(inviteJoinPort.issueGuestJoin(eq("meeting-a"), startsWith("guest:")))
        .thenThrow(new IllegalStateException("token issue failed"));

    assertThatThrownBy(() -> service.exchange("invite-token", "Guest User"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("token issue failed");

    assertThat(inviteReservationCapability.lastReservedToken).isEqualTo("invite-token");
    assertThat(inviteReservationCapability.lastRolledBackReservation).isEqualTo(reservation);
  }

  @Test
  void exchangePreservesOriginalFailureAndAttachesRollbackFailure() {
    InviteReservation reservation = InviteReservation.issue("reservation-rollback-fail", "invite-token", "meeting-a");
    inviteReservationCapability.reservation = reservation;
    inviteReservationCapability.rollbackFailure = new IllegalStateException("rollback failed");
    when(inviteJoinPort.issueGuestJoin(eq("meeting-a"), startsWith("guest:")))
        .thenThrow(new IllegalStateException("token issue failed"));

    assertThatThrownBy(() -> service.exchange("invite-token", "Guest User"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("token issue failed")
        .satisfies(error -> {
          Throwable[] suppressed = error.getSuppressed();
          assertThat(suppressed).hasSize(1);
          assertThat(suppressed[0]).isInstanceOf(IllegalStateException.class).hasMessage("rollback failed");
        });

    assertThat(inviteReservationCapability.lastRolledBackReservation).isEqualTo(reservation);
  }

  @Test
  void exchangeReturnsJoinPayloadFromReservationAndTokenIssuer() {
    InviteReservation reservation = InviteReservation.issue("reservation-2", "invite-token", "meeting-a");
    InviteJoinPort.JoinResult tokenResult =
      new InviteJoinPort.JoinResult("https://meet.example/join", Instant.parse("2099-01-01T10:15:30Z"), "participant");
    inviteReservationCapability.reservation = reservation;
    when(inviteJoinPort.issueGuestJoin(eq("meeting-a"), startsWith("guest:")))
        .thenReturn(tokenResult);

    InviteExchangeService.ExchangeResult result = service.exchange("invite-token", "Guest User");

    assertThat(result.joinUrl()).isEqualTo("https://meet.example/join");
    assertThat(result.expiresAt()).isEqualTo(Instant.parse("2099-01-01T10:15:30Z"));
    assertThat(result.role()).isEqualTo("participant");
    assertThat(result.meetingId()).isEqualTo("meeting-a");
    assertThat(inviteReservationCapability.lastReservedToken).isEqualTo("invite-token");
    assertThat(inviteReservationCapability.lastRolledBackReservation).isNull();
  }

  private static final class FakeInviteValidationCapability implements InviteValidationCapability {

    private InviteValidationResult result;
    private String lastValidatedToken;

    @Override
    public InviteValidationResult validate(String inviteToken) {
      lastValidatedToken = inviteToken;
      return result;
    }
  }

  private static final class FakeInviteReservationCapability implements InviteReservationCapability {

    private InviteReservation reservation;
    private String lastReservedToken;
    private InviteReservation lastRolledBackReservation;
    private RuntimeException rollbackFailure;

    @Override
    public InviteReservation reserve(String inviteToken) {
      lastReservedToken = inviteToken;
      return reservation;
    }

    @Override
    public void rollback(InviteReservation reservation) {
      lastRolledBackReservation = reservation;
      if (rollbackFailure != null) {
        throw rollbackFailure;
      }
    }
  }
}