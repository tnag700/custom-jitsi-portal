package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomService;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

  @Mock
  private MeetingRepository meetingRepository;

  @Mock
  private RoomService roomService;

  private MeetingService meetingService;

  @BeforeEach
  void setUp() {
    meetingService = new MeetingService(meetingRepository, roomService);
  }

  @Test
  void getMeetingReturnsEntity() {
    Meeting existing = new Meeting(
        "meeting-1",
        "room-1",
        "Title",
        "Description",
        "scheduled",
        "config-1",
        MeetingStatus.SCHEDULED,
        Instant.parse("2026-02-17T10:00:00Z"),
        Instant.parse("2026-02-17T11:00:00Z"),
        true,
        false,
        Instant.now(),
        Instant.now());
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(existing));
    Meeting found = meetingService.getMeeting("meeting-1");
    assertThat(found.meetingId()).isEqualTo("meeting-1");
  }

  @Test
  void listMeetingsWithNegativePageThrowsInvalidMeetingDataException() {
    assertThatThrownBy(() -> meetingService.listMeetings("room-1", -1, 20))
        .isInstanceOf(InvalidMeetingDataException.class)
        .hasMessageContaining("Page");
  }

  @Test
  void listMeetingsWithNonPositiveSizeThrowsInvalidMeetingDataException() {
    assertThatThrownBy(() -> meetingService.listMeetings("room-1", 0, 0))
        .isInstanceOf(InvalidMeetingDataException.class)
        .hasMessageContaining("Size");
  }

  @Test
  void listAndCountMeetingsValidateRoomAndReturnValues() {
    Room room = new Room("room-1", "Room", null, "tenant-1", "config-1", RoomStatus.ACTIVE, Instant.now(), Instant.now());
    when(roomService.getRoom("room-1")).thenReturn(room);
    when(meetingRepository.findByRoomId("room-1", 0, 20)).thenReturn(List.of());
    when(meetingRepository.countByRoomId("room-1")).thenReturn(0L);

    assertThat(meetingService.listMeetings("room-1", 0, 20)).isEmpty();
    assertThat(meetingService.countMeetings("room-1")).isZero();
  }
}