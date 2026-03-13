package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.invites.infrastructure.DbInviteValidationAdapter;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteUseCase;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class DbInviteValidationAdapterTest {

  @Mock
  private MeetingInviteService inviteService;

  @Mock
  private ConsumeInviteUseCase consumeInviteUseCase;

  @Mock
  private InviteMeetingStatePort inviteMeetingStatePort;

  private DbInviteValidationAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new DbInviteValidationAdapter(inviteService, consumeInviteUseCase, inviteMeetingStatePort);
  }

  @Test
  void validateChecksInviteAndReturnsResolution() {
    MeetingInvite invite = new MeetingInvite(
        "invite-validate",
        "meeting-validate",
        "token-validate",
        MeetingRole.PARTICIPANT,
        3,
        0,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "admin-1");
    when(inviteService.findByToken("token-validate")).thenReturn(Optional.of(invite));

    InviteValidationResult resolution = adapter.validate("token-validate");

    assertThat(resolution.meetingId()).isEqualTo("meeting-validate");
    verify(inviteService).findByToken("token-validate");
    verify(inviteMeetingStatePort).assertJoinAllowed("meeting-validate");
  }

  @Test
  void reserveConsumesInviteAndReturnsReservation() {
    MeetingInvite invite = new MeetingInvite(
        "invite-1",
        "meeting-1",
        "token-1",
        MeetingRole.PARTICIPANT,
        3,
        1,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "admin-1");
    when(consumeInviteUseCase.execute(new ConsumeInviteCommand("token-1"))).thenReturn(invite);

    InviteReservation reservation = adapter.reserve("token-1");

    assertThat(reservation.inviteToken()).isEqualTo("token-1");
    assertThat(reservation.meetingId()).isEqualTo("meeting-1");
    verify(consumeInviteUseCase).execute(new ConsumeInviteCommand("token-1"));
  }

  @Test
  void rollbackRestoresUsageCounter() {
    MeetingInvite invite = new MeetingInvite(
        "invite-rollback",
        "meeting-1",
        "token-rollback",
        MeetingRole.PARTICIPANT,
        3,
        1,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "admin-1");
    when(consumeInviteUseCase.execute(new ConsumeInviteCommand("token-rollback"))).thenReturn(invite);

    InviteReservation reservation = adapter.reserve("token-rollback");

    adapter.rollback(reservation);

    verify(consumeInviteUseCase).execute(new ConsumeInviteCommand("token-rollback"));
    verify(inviteService).rollbackConsume("token-rollback");
  }

  @Test
  void rollbackIgnoresForgedReservation() {
    adapter.rollback(InviteReservation.issue("forged", "token-rollback", "meeting-1"));

    verify(inviteService, org.mockito.Mockito.never()).rollbackConsume("token-rollback");
  }

  @Test
  void validatePrefersMeetingStateFailureOverExhaustedInvite() {
    MeetingInvite exhaustedInvite = new MeetingInvite(
        "invite-exhausted",
        "meeting-ended",
        "token-ended",
        MeetingRole.PARTICIPANT,
        1,
        1,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "admin-1");
    when(inviteService.findByToken("token-ended")).thenReturn(Optional.of(exhaustedInvite));
    doThrow(new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена."))
        .when(inviteMeetingStatePort).assertJoinAllowed("meeting-ended");

    assertThatThrownBy(() -> adapter.validate("token-ended"))
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException exception = (InviteExchangeException) error;
          assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEETING_ENDED.code());
          assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        });
  }
}
