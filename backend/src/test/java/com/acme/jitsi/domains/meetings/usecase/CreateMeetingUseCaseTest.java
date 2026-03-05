package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingScheduleException;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRoomInactiveException;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.domains.rooms.service.ConfigSetInvalidException;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
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
class CreateMeetingUseCaseTest {

  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private ConfigSetValidator configSetValidator;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CreateMeetingUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateMeetingUseCase(
        meetingRepository,
        configSetValidator,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeCreatesMeetingAndPublishesEvent() {
    Room room = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE, Instant.now(), Instant.now());
    when(configSetValidator.isValid("config-1")).thenReturn(true);
    when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Meeting meeting = useCase.execute(new CreateMeetingCommand(
        room,
        "Title",
        "Description",
        "scheduled",
        Instant.parse("2026-02-17T10:00:00Z"),
        Instant.parse("2026-02-17T11:00:00Z"),
        true,
        false,
        "actor-1",
        "trace-1"));

    assertThat(meeting.roomId()).isEqualTo("room-1");
    assertThat(meeting.status()).isEqualTo(MeetingStatus.SCHEDULED);
    verify(eventPublisher).publishEvent(any(MeetingCreatedEvent.class));
  }

  @Test
  void executeThrowsWhenRoomIsInactive() {
    Room inactiveRoom = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.CLOSED, Instant.now(), Instant.now());

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        inactiveRoom, "Title", "Desc", "scheduled",
        Instant.parse("2026-02-17T10:00:00Z"), Instant.parse("2026-02-17T11:00:00Z"),
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(MeetingRoomInactiveException.class);
  }

  @Test
  void executeThrowsWhenConfigSetIsInvalid() {
    Room room = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE, Instant.now(), Instant.now());
    when(configSetValidator.isValid("config-1")).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        room, "Title", "Desc", "scheduled",
        Instant.parse("2026-02-17T10:00:00Z"), Instant.parse("2026-02-17T11:00:00Z"),
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(ConfigSetInvalidException.class);
  }

  @Test
  void executeThrowsWhenScheduleIsInvalid() {
    Room room = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE, Instant.now(), Instant.now());
    when(configSetValidator.isValid("config-1")).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        room, "Title", "Desc", "scheduled",
        Instant.parse("2026-02-17T11:00:00Z"),  // startsAt AFTER endsAt
        Instant.parse("2026-02-17T10:00:00Z"),
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(InvalidMeetingScheduleException.class);
  }
}
