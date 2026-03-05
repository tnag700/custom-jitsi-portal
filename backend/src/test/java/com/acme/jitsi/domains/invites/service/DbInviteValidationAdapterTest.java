package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteUseCase;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbInviteValidationAdapterTest {

  @Mock
  private MeetingInviteService inviteService;

  @Mock
  private ConsumeInviteUseCase consumeInviteUseCase;

  @Mock
  private MeetingStateGuard meetingStateGuard;

  private DbInviteValidationAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new DbInviteValidationAdapter(inviteService, consumeInviteUseCase, meetingStateGuard);
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

    InviteValidationService.InviteResolution resolution = adapter.validate("token-validate");

    assertThat(resolution.meetingId()).isEqualTo("meeting-validate");
    verify(inviteService).findByToken("token-validate");
    verify(meetingStateGuard).assertJoinAllowed("meeting-validate");
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

    InviteValidationService.InviteReservation reservation = adapter.reserve("token-1");

    assertThat(reservation.inviteToken()).isEqualTo("token-1");
    assertThat(reservation.meetingId()).isEqualTo("meeting-1");
    verify(consumeInviteUseCase).execute(new ConsumeInviteCommand("token-1"));
  }

  @Test
  void rollbackRestoresUsageCounter() {
    InviteValidationService.InviteReservation reservation =
        new InviteValidationService.InviteReservation("token-rollback", "meeting-1");

    adapter.rollback(reservation);

    verify(inviteService).rollbackConsume("token-rollback");
  }

  @Test
  void validateAndConsumeReturnsResolution() {
    MeetingInvite invite = new MeetingInvite(
        "invite-2",
        "meeting-2",
        "token-2",
        MeetingRole.PARTICIPANT,
        1,
        1,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "admin-1");
    when(consumeInviteUseCase.execute(new ConsumeInviteCommand("token-2"))).thenReturn(invite);

    InviteValidationService.InviteResolution resolution = adapter.validateAndConsume("token-2");

    assertThat(resolution.meetingId()).isEqualTo("meeting-2");
    verify(consumeInviteUseCase).execute(new ConsumeInviteCommand("token-2"));
  }
}
