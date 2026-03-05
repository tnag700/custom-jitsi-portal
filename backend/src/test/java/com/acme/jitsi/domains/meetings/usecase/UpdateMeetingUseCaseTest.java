package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingUpdatedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingFinalizedException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UpdateMeetingUseCaseTest {

  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private UpdateMeetingUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateMeetingUseCase(
        meetingRepository,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeUpdatesMeetingAndPublishesEvent() {
    Meeting existing = new Meeting(
        "meeting-1",
        "room-1",
        "Old title",
        "Old description",
        "scheduled",
        "config-1",
        MeetingStatus.SCHEDULED,
        Instant.parse("2026-02-17T10:00:00Z"),
        Instant.parse("2026-02-17T11:00:00Z"),
        true,
        false,
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-01T00:00:00Z"));

    when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Meeting updated = useCase.execute(new UpdateMeetingCommand(
        existing,
        "New title",
        null,
        null,
        null,
        null,
        null,
        null,
        "actor-1",
        "trace-1"));

    assertThat(updated.title()).isEqualTo("New title");
    verify(eventPublisher).publishEvent(any(MeetingUpdatedEvent.class));
  }

  @Test
  void executeThrowsWhenMeetingIsFinalized() {
    Meeting finalized = new Meeting(
        "meeting-1", "room-1", "Title", null, "scheduled", "config-1",
        MeetingStatus.CANCELED,
        Instant.parse("2026-02-17T10:00:00Z"), Instant.parse("2026-02-17T11:00:00Z"),
        true, false,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

    assertThatThrownBy(() -> useCase.execute(new UpdateMeetingCommand(
        finalized, "New title", null, null, null, null, null, null, "actor-1", "trace-1")))
        .isInstanceOf(MeetingFinalizedException.class);
  }
}
