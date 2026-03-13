package com.acme.jitsi.domains.meetings.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
class ConfiguredUpcomingMeetingsService implements UpcomingMeetingsReader {

  private static final long JOIN_AVAILABLE_WINDOW_SECONDS = 15L * 60L;

  private final MeetingTokenProperties properties;
  private final MeetingParticipantAssignmentRepository assignmentRepository;
  private final MeetingService meetingService;
  private final MeetingRoomsPort meetingRoomsPort;
  private final Clock clock;

  ConfiguredUpcomingMeetingsService(
      MeetingTokenProperties properties,
      MeetingParticipantAssignmentRepository assignmentRepository,
      MeetingService meetingService,
      MeetingRoomsPort meetingRoomsPort,
      Clock clock) {
    this.properties = properties;
    this.assignmentRepository = assignmentRepository;
    this.meetingService = meetingService;
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
    List<MeetingParticipantAssignment> assignments = assignmentRepository.findBySubjectId(subject);
    if (assignments.isEmpty()) {
      return List.of();
    }

    Map<String, String> roomNameByMeetingId = new HashMap<>();

    return assignments.stream()
        .map(assignment -> safeGetMeeting(assignment.meetingId()))
        .filter(meeting -> meeting != null)
        .filter(meeting -> meeting.status() == MeetingStatus.SCHEDULED)
        .filter(meeting -> isFutureOrOngoing(meeting, now))
        .sorted(Comparator.comparing(Meeting::startsAt))
        .map(meeting -> new UpcomingMeetingCard(
            meeting.meetingId(),
            meeting.title(),
            meeting.startsAt(),
            roomNameByMeetingId.computeIfAbsent(meeting.meetingId(), ignored -> safeResolveRoomName(meeting.roomId())),
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

  private Meeting safeGetMeeting(String meetingId) {
    try {
      return meetingService.getMeeting(meetingId);
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private String safeResolveRoomName(String roomId) {
    try {
      return meetingRoomsPort.getRequiredRoom(roomId).name();
    } catch (RuntimeException ignored) {
      return roomId;
    }
  }

  private boolean isFutureOrOngoing(Meeting meeting, Instant now) {
    if (meeting.startsAt() == null) {
      return false;
    }

    if (!meeting.startsAt().isBefore(now)) {
      return true;
    }

    return meeting.endsAt() != null && !meeting.endsAt().isBefore(now);
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
