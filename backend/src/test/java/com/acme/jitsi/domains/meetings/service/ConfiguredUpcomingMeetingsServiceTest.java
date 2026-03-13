package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfiguredUpcomingMeetingsServiceTest {

  @Test
  void returnsOnlyUpcomingAssignedMeetingsSortedByStartTime() {
    Instant now = Instant.parse("2026-02-16T10:00:00Z");

    MeetingTokenProperties properties = new MeetingTokenProperties();

    MeetingTokenProperties.RoleAssignment assignmentA = new MeetingTokenProperties.RoleAssignment();
    assignmentA.setMeetingId("meeting-a");
    assignmentA.setSubject("u-1");
    assignmentA.setRole("participant");

    MeetingTokenProperties.RoleAssignment assignmentB = new MeetingTokenProperties.RoleAssignment();
    assignmentB.setMeetingId("meeting-b");
    assignmentB.setSubject("u-1");
    assignmentB.setRole("host");

    MeetingTokenProperties.RoleAssignment assignmentOther = new MeetingTokenProperties.RoleAssignment();
    assignmentOther.setMeetingId("meeting-x");
    assignmentOther.setSubject("u-2");
    assignmentOther.setRole("participant");

    properties.setAssignments(List.of(assignmentA, assignmentB, assignmentOther));

    MeetingTokenProperties.UpcomingMeetingDefinition meetingA = new MeetingTokenProperties.UpcomingMeetingDefinition();
    meetingA.setMeetingId("meeting-a");
    meetingA.setTitle("Daily Sync");
    meetingA.setStartsAt(now.plusSeconds(30 * 60));
    meetingA.setRoomName("Room A");

    MeetingTokenProperties.UpcomingMeetingDefinition meetingB = new MeetingTokenProperties.UpcomingMeetingDefinition();
    meetingB.setMeetingId("meeting-b");
    meetingB.setTitle("Incident Review");
    meetingB.setStartsAt(now.plusSeconds(5 * 60));
    meetingB.setRoomName("Room B");

    MeetingTokenProperties.UpcomingMeetingDefinition meetingPast = new MeetingTokenProperties.UpcomingMeetingDefinition();
    meetingPast.setMeetingId("meeting-past");
    meetingPast.setTitle("Past Meeting");
    meetingPast.setStartsAt(now.minusSeconds(60));
    meetingPast.setRoomName("Archive");

    MeetingTokenProperties.UpcomingMeetingDefinition meetingOther = new MeetingTokenProperties.UpcomingMeetingDefinition();
    meetingOther.setMeetingId("meeting-x");
    meetingOther.setTitle("Other User Meeting");
    meetingOther.setStartsAt(now.plusSeconds(15 * 60));
    meetingOther.setRoomName("Room X");

    properties.setUpcomingMeetings(List.of(meetingA, meetingB, meetingPast, meetingOther));

    MeetingParticipantAssignmentRepository assignmentRepository =
      mock(MeetingParticipantAssignmentRepository.class);
    when(assignmentRepository.findBySubjectId("u-1")).thenReturn(List.of());

    MeetingService meetingService = mock(MeetingService.class);
    MeetingRoomsPort meetingRoomsPort = mock(MeetingRoomsPort.class);

    UpcomingMeetingsReader reader = new ConfiguredUpcomingMeetingsService(
        properties,
      assignmentRepository,
      meetingService,
      meetingRoomsPort,
        Clock.fixed(now, ZoneOffset.UTC));

    List<UpcomingMeetingCard> cards = reader.listForSubject("u-1");

    assertThat(cards)
        .extracting(UpcomingMeetingCard::meetingId)
        .containsExactly("meeting-b", "meeting-a");

    assertThat(cards)
        .extracting(UpcomingMeetingCard::joinAvailability)
        .containsExactly(JoinAvailability.AVAILABLE, JoinAvailability.SCHEDULED);
  }
}
