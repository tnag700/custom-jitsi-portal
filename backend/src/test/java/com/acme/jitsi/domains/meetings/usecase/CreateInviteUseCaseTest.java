package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CreateInviteUseCaseTest {

  @Mock
  private MeetingInviteRepository inviteRepository;
  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CreateInviteUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateInviteUseCase(
        inviteRepository,
        meetingRepository,
        eventPublisher,
        new SecureInviteTokenGenerator(),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeCreatesInviteAndPublishesEvent() {
    Meeting meeting = mock(Meeting.class);
    when(meeting.roomId()).thenReturn("room-1");
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
    when(inviteRepository.save(any(MeetingInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MeetingInvite invite = useCase.execute(new CreateInviteCommand(
        "meeting-1",
        MeetingRole.PARTICIPANT,
        2,
        Instant.parse("2026-01-01T01:00:00Z"),
        "actor-1",
        "trace-1"));

    assertThat(invite.meetingId()).isEqualTo("meeting-1");
    verify(eventPublisher).publishEvent(any(MeetingInviteCreatedEvent.class));
  }
}
