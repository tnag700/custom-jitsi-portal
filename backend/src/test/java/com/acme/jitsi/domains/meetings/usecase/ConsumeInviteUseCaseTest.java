package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteExpiredException;
import com.acme.jitsi.domains.meetings.service.InviteRevokedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
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
class ConsumeInviteUseCaseTest {

  @Mock
  private MeetingInviteRepository inviteRepository;
  @Mock
  private MeetingStateGuard meetingStateGuard;

  private ConsumeInviteAttemptExecutor attemptExecutor;

  @BeforeEach
  void setUp() {
    attemptExecutor = new ConsumeInviteAttemptExecutor(inviteRepository, meetingStateGuard);
  }

  @Test
  void executeConsumesInvite() {
    MeetingInvite invite = new MeetingInvite(
        "invite-1",
        "meeting-1",
        "token-1",
        MeetingRole.PARTICIPANT,
        2,
        0,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "creator");

    when(inviteRepository.findByToken("token-1")).thenReturn(Optional.of(invite));
    when(inviteRepository.save(any(MeetingInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MeetingInvite consumed = attemptExecutor.execute(new ConsumeInviteCommand("token-1"));

    assertThat(consumed.usedCount()).isEqualTo(1);
  }

  @Test
  void executeThrowsWhenInviteIsRevoked() {
    MeetingInvite revoked = new MeetingInvite(
        "invite-1", "meeting-1", "tok", MeetingRole.PARTICIPANT,
        2, 0, Instant.now().plusSeconds(3600),
        Instant.now().minusSeconds(10),  // revokedAt
        Instant.now(), "creator");
    when(inviteRepository.findByToken("tok")).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> attemptExecutor.execute(new ConsumeInviteCommand("tok")))
        .isInstanceOf(InviteRevokedException.class);
  }

  @Test
  void executeThrowsWhenInviteIsExpired() {
    MeetingInvite expired = new MeetingInvite(
        "invite-1", "meeting-1", "tok", MeetingRole.PARTICIPANT,
        2, 0, Instant.now().minusSeconds(3600),  // expiresAt in the past
        null, Instant.now(), "creator");
    when(inviteRepository.findByToken("tok")).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> attemptExecutor.execute(new ConsumeInviteCommand("tok")))
        .isInstanceOf(InviteExpiredException.class);
  }

  @Test
  void executeThrowsWhenInviteIsExhausted() {
    MeetingInvite exhausted = new MeetingInvite(
        "invite-1", "meeting-1", "tok", MeetingRole.PARTICIPANT,
        2, 2,  // usedCount == maxUses
        Instant.now().plusSeconds(3600), null, Instant.now(), "creator");
    when(inviteRepository.findByToken("tok")).thenReturn(Optional.of(exhausted));

    assertThatThrownBy(() -> attemptExecutor.execute(new ConsumeInviteCommand("tok")))
        .isInstanceOf(InviteExhaustedException.class);
  }

  @Test
  void executePrefersMeetingStateFailureOverExhaustedInvite() {
    MeetingInvite exhausted = new MeetingInvite(
        "invite-1", "meeting-1", "tok", MeetingRole.PARTICIPANT,
        2, 2,
        Instant.now().plusSeconds(3600), null, Instant.now(), "creator");
    when(inviteRepository.findByToken("tok")).thenReturn(Optional.of(exhausted));
    doThrow(new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена."))
        .when(meetingStateGuard).assertJoinAllowed("meeting-1");

    assertThatThrownBy(() -> attemptExecutor.execute(new ConsumeInviteCommand("tok")))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> assertThat(((MeetingTokenException) error).errorCode()).isEqualTo(ErrorCode.MEETING_ENDED.code()));
  }
}
