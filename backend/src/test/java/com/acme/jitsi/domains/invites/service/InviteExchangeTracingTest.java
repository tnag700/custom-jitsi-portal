package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import com.acme.jitsi.shared.observability.RecordedObservationHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteExchangeTracingTest {

  private final RecordedObservationHandler observations = new RecordedObservationHandler();

  private FakeInviteReservationCapability inviteReservationCapability;
  private InviteJoinPort inviteJoinPort;
  private InviteExchangeService service;

  @BeforeEach
  void setUp() {
    inviteReservationCapability = new FakeInviteReservationCapability();
    inviteJoinPort = org.mockito.Mockito.mock(InviteJoinPort.class);
    service = new InviteExchangeService(
        inviteToken -> new InviteValidationResult("meeting-a"),
        inviteReservationCapability,
        inviteJoinPort,
        new FlowObservationFacade(observations.createRegistry()));
    observations.reset();
  }

  @Test
  void exchangeEmitsCanonicalSuccessObservationWithoutSensitiveValues() {
    inviteReservationCapability.reservation = InviteReservation.issue("reservation-1", "invite-token", "meeting-a");
    when(inviteJoinPort.issueGuestJoin(eq("meeting-a"), startsWith("guest:")))
        .thenReturn(new InviteJoinPort.JoinResult("https://meet.example/join", Instant.parse("2099-01-01T10:15:30Z"), "participant"));

    service.exchange("invite-token", "Guest User");

    RecordedObservationHandler.RecordedObservation observation = observations.only("invite.exchange");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "success")
        .containsEntry("flow.stage", "issue_token")
        .containsEntry("flow.rollback", "skipped")
        .containsEntry("flow.guest", "true");
    assertThat(observation.lowCardinality().toString())
        .doesNotContain("invite-token")
        .doesNotContain("Guest User")
        .doesNotContain("https://meet.example/join");
  }

  @Test
  void exchangeMarksValidationFailureWhenReservationRejectsInvite() {
    inviteReservationCapability.reserveFailure = new InviteExchangeException(
        HttpStatus.GONE,
        ErrorCode.INVITE_REVOKED.code(),
        "Инвайт отозван.");

    assertThatThrownBy(() -> service.exchange("invite-token", "Guest User"))
        .isInstanceOf(InviteExchangeException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("invite.exchange");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "validation_failure")
        .containsEntry("flow.stage", "validate")
        .containsEntry("flow.rollback", "skipped");
  }

  @Test
  void exchangeMarksContentionWhenReservationDetectsUsageConflict() {
    inviteReservationCapability.reserveFailure = new InviteExchangeException(
        HttpStatus.CONFLICT,
        ErrorCode.INVITE_EXHAUSTED.code(),
        "Лимит использований инвайта исчерпан.");

    assertThatThrownBy(() -> service.exchange("invite-token", "Guest User"))
        .isInstanceOf(InviteExchangeException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("invite.exchange");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "contention")
        .containsEntry("flow.stage", "reserve")
        .containsEntry("flow.rollback", "skipped");
  }

  @Test
  void exchangeMarksPartialFailureAndRollbackWhenGuestTokenIssuanceFails() {
    InviteReservation reservation = InviteReservation.issue("reservation-2", "invite-token", "meeting-a");
    inviteReservationCapability.reservation = reservation;
    when(inviteJoinPort.issueGuestJoin(eq("meeting-a"), startsWith("guest:")))
        .thenThrow(new IllegalStateException("token issue failed"));

    assertThatThrownBy(() -> service.exchange("invite-token", "Guest User"))
        .isInstanceOf(IllegalStateException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("invite.exchange");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "partial_failure")
        .containsEntry("flow.stage", "issue_token")
        .containsEntry("flow.rollback", "performed");
  }

  @Test
  void exchangeMarksRollbackFailedWhenBothTokenIssuanceAndRollbackThrow() {
    InviteReservation reservation = InviteReservation.issue("reservation-3", "invite-token", "meeting-a");
    inviteReservationCapability.reservation = reservation;
    inviteReservationCapability.rollbackFailure = new IllegalStateException("rollback also failed");
    when(inviteJoinPort.issueGuestJoin(eq("meeting-a"), startsWith("guest:")))
        .thenThrow(new IllegalStateException("token issue failed"));

    assertThatThrownBy(() -> service.exchange("invite-token", "Guest User"))
        .isInstanceOf(IllegalStateException.class)
        .satisfies(ex -> assertThat(ex.getSuppressed()).hasSize(1));

    RecordedObservationHandler.RecordedObservation observation = observations.only("invite.exchange");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "partial_failure")
        .containsEntry("flow.stage", "issue_token")
        .containsEntry("flow.rollback", "failed");
  }

  private static final class FakeInviteReservationCapability implements InviteReservationCapability {

    private InviteReservation reservation;
    private RuntimeException reserveFailure;
    private RuntimeException rollbackFailure;

    @Override
    public InviteReservation reserve(String inviteToken) {
      if (reserveFailure != null) {
        throw reserveFailure;
      }
      return reservation;
    }

    @Override
    public void rollback(InviteReservation reservation) {
      if (rollbackFailure != null) {
        throw rollbackFailure;
      }
    }
  }
}