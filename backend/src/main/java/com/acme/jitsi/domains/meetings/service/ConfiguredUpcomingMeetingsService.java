package com.acme.jitsi.domains.meetings.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class ConfiguredUpcomingMeetingsService implements UpcomingMeetingsReader {

  private static final long JOIN_AVAILABLE_WINDOW_SECONDS = 15L * 60L;

  private final MeetingTokenProperties properties;
  private final MeetingRepository meetingRepository;
  private final MeetingRoomsPort meetingRoomsPort;
  private final Clock clock;

  ConfiguredUpcomingMeetingsService(
      MeetingTokenProperties properties,
      MeetingRepository meetingRepository,
      MeetingRoomsPort meetingRoomsPort,
      Clock clock) {
    this.properties = properties;
    this.meetingRepository = meetingRepository;
    this.meetingRoomsPort = meetingRoomsPort;
    this.clock = clock;
  }

  @Override
  public List<UpcomingMeetingCard> listForSubject(String subject) {
    Instant now = Instant.now(clock);

    List<UpcomingMeetingCard> fromDb = listFromDatabase(subject, now);
    if (!fromDb.isEmpty()) {
      return fromDb;
    }

    return listFromConfiguredProperties(subject, now);
  }

  private List<UpcomingMeetingCard> listFromDatabase(String subject, Instant now) {
    List<Meeting> meetings = meetingRepository.findUpcomingBySubjectId(subject, now);
    if (meetings.isEmpty()) {
      return List.of();
    }

    Map<String, String> roomNames = meetingRoomsPort.getRoomNames(
        meetings.stream().map(Meeting::roomId).collect(Collectors.toSet()));

    return meetings.stream()
        .map(meeting -> new UpcomingMeetingCard(
            meeting.meetingId(),
            meeting.title(),
            meeting.startsAt(),
            roomNames.getOrDefault(meeting.roomId(), meeting.roomId()),
            resolveJoinAvailability(now, meeting.startsAt())))
        .toList();
  }

  private List<UpcomingMeetingCard> listFromConfiguredProperties(String subject, Instant now) {
    Set<String> assignedMeetingIds = properties.assignments().stream()
        .filter(assignment -> subject.equals(assignment.subject()))
        .map(MeetingTokenProperties.RoleAssignment::meetingId)
        .collect(Collectors.toSet());

    return properties.upcomingMeetings().stream()
        .filter(meeting -> assignedMeetingIds.contains(meeting.meetingId()))
        .filter(meeting -> meeting.startsAt() != null && !meeting.startsAt().isBefore(now))
        .sorted(Comparator.comparing(MeetingTokenProperties.UpcomingMeetingDefinition::startsAt))
        .map(meeting -> new UpcomingMeetingCard(
            meeting.meetingId(),
            meeting.title(),
            meeting.startsAt(),
            meeting.roomName(),
            resolveJoinAvailability(now, meeting.startsAt())))
        .toList();
  }

  private JoinAvailability resolveJoinAvailability(Instant now, Instant startsAt) {
    if (!startsAt.isAfter(now)) {
      return JoinAvailability.AVAILABLE;
    }
    if (!startsAt.isAfter(now.plusSeconds(JOIN_AVAILABLE_WINDOW_SECONDS))) {
      return JoinAvailability.AVAILABLE;
    }
    return JoinAvailability.SCHEDULED;
  }
}
