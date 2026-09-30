package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingScheduleException;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingConfigSetInvalidException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRoomSnapshot;
import com.acme.jitsi.domains.meetings.service.MeetingRoomInactiveException;
import com.acme.jitsi.domains.meetings.service.MeetingRoomsPort;
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
class CreateMeetingUseCaseTest {

  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private MeetingRoomsPort meetingRoomsPort;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private CreateMeetingUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new CreateMeetingUseCase(
        meetingRepository,
      meetingRoomsPort,
        eventPublisher,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeCreatesMeetingAndPublishesEvent() {
    when(meetingRoomsPort.getRequiredRoomForUpdate("room-1")).thenReturn(activeRoom("room-1", "config-1"));
    when(meetingRoomsPort.isConfigSetValid(activeRoom("room-1", "config-1"))).thenReturn(true);
    when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Meeting meeting = useCase.execute(new CreateMeetingCommand(
      "room-1",
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
    when(meetingRoomsPort.getRequiredRoomForUpdate("room-1")).thenReturn(inactiveRoom("room-1", "config-1"));
    when(meetingRoomsPort.isConfigSetValid(inactiveRoom("room-1", "config-1"))).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        "room-1", "Title", "Desc", "scheduled",
        Instant.parse("2026-02-17T10:00:00Z"), Instant.parse("2026-02-17T11:00:00Z"),
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(MeetingRoomInactiveException.class);
  }

  @Test
  void executeThrowsWhenConfigSetIsInvalid() {
    when(meetingRoomsPort.getRequiredRoomForUpdate("room-1")).thenReturn(activeRoom("room-1", "config-1"));
    when(meetingRoomsPort.isConfigSetValid(activeRoom("room-1", "config-1"))).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        "room-1", "Title", "Desc", "scheduled",
        Instant.EPOCH, Instant.EPOCH,
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(MeetingConfigSetInvalidException.class);
  }

  @Test
  void executeThrowsWhenScheduleIsInvalid() {
    when(meetingRoomsPort.getRequiredRoomForUpdate("room-1")).thenReturn(activeRoom("room-1", "config-1"));
    when(meetingRoomsPort.isConfigSetValid(activeRoom("room-1", "config-1"))).thenReturn(true);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        "room-1", "Title", "Desc", "scheduled",
        Instant.parse("2026-02-17T11:00:00Z"),  // startsAt AFTER endsAt
        Instant.parse("2026-02-17T10:00:00Z"),
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(InvalidMeetingScheduleException.class);
  }

  @Test
  void inactiveRoomPrecedesInvalidConfigurationAndSchedule() {
    var room = inactiveRoom("room-1", "config-1");
    when(meetingRoomsPort.getRequiredRoomForUpdate("room-1")).thenReturn(room);
    when(meetingRoomsPort.isConfigSetValid(room)).thenReturn(false);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        "room-1", "Title", "Desc", "scheduled", Instant.EPOCH, Instant.EPOCH,
        true, false, "actor-1", "trace-1")))
        .isInstanceOf(MeetingRoomInactiveException.class);
    verifyNoInteractions(meetingRepository, eventPublisher);
  }

  @Test
  void configurationStorageFailurePrecedesRoomAndScheduleRejection() {
    var room = inactiveRoom("room-1", "config-1");
    IllegalStateException failure = new IllegalStateException("Configuration storage unavailable");
    when(meetingRoomsPort.getRequiredRoomForUpdate("room-1")).thenReturn(room);
    when(meetingRoomsPort.isConfigSetValid(room)).thenThrow(failure);

    assertThatThrownBy(() -> useCase.execute(new CreateMeetingCommand(
        "room-1", "Title", "Desc", "scheduled", Instant.EPOCH, Instant.EPOCH,
        true, false, "actor-1", "trace-1")))
        .isSameAs(failure);
    verifyNoInteractions(meetingRepository, eventPublisher);
  }

  private MeetingRoomSnapshot activeRoom(String roomId, String configSetId) {
    return new MeetingRoomSnapshot(roomId, "Room", "tenant-1", configSetId, true);
  }

  private MeetingRoomSnapshot inactiveRoom(String roomId, String configSetId) {
    return new MeetingRoomSnapshot(roomId, "Room", "tenant-1", configSetId, false);
  }
}
