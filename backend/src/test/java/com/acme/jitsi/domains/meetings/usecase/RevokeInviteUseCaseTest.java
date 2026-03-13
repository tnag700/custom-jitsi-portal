package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingInviteRevokedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.shared.TestFixtures;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class RevokeInviteUseCaseTest {

  @Mock
  private MeetingInviteRepository inviteRepository;
  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private RevokeInviteUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new RevokeInviteUseCase(inviteRepository, meetingRepository, eventPublisher);
  }

  @Test
  void executeRevokesInviteAndPublishesEvent() {
    MeetingInvite invite = new MeetingInvite(
        "invite-1",
        "meeting-1",
        "token",
        MeetingRole.PARTICIPANT,
        1,
        0,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "creator");
    Meeting meeting = TestFixtures.mockMeeting("meeting-1", "room-1");
    when(inviteRepository.findById("invite-1")).thenReturn(Optional.of(invite));
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
    when(inviteRepository.save(any(MeetingInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MeetingInvite revoked = useCase.execute(new RevokeInviteCommand("meeting-1", "invite-1", "actor-1", "trace-1"));

    assertThat(revoked.isRevoked()).isTrue();
    verify(eventPublisher).publishEvent(any(MeetingInviteRevokedEvent.class));
  }
}
