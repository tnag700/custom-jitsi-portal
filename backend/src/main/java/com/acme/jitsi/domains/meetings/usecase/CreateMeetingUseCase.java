package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingDataException;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingScheduleException;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRoomInactiveException;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.domains.rooms.service.ConfigSetInvalidException;
import com.acme.jitsi.domains.rooms.service.ConfigSetValidator;
import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateMeetingUseCase implements UseCase<CreateMeetingCommand, Meeting> {

  private static final String MEETING_ALL_FIELDS =
      "title,description,meetingType,startsAt,endsAt,allowGuests,recordingEnabled";

  private final MeetingRepository meetingRepository;
  private final ConfigSetValidator configSetValidator;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public CreateMeetingUseCase(
      MeetingRepository meetingRepository,
      ConfigSetValidator configSetValidator,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.meetingRepository = meetingRepository;
    this.configSetValidator = configSetValidator;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Meeting execute(CreateMeetingCommand command) {
    Room room = command.room();
    validateRoomIsActive(room);
    validateRoomConfigSet(room.configSetId());
    validateSchedule(command.startsAt(), command.endsAt());

    Instant now = Instant.now(clock);
    Meeting meeting = new Meeting(
        UUID.randomUUID().toString(),
        room.roomId(),
        normalizeRequired(command.title(), "Meeting title is required"),
        normalizeOptional(command.description()),
        normalizeRequired(command.meetingType(), "Meeting type is required"),
        room.configSetId(),
        MeetingStatus.SCHEDULED,
        command.startsAt(),
        command.endsAt(),
        command.allowGuests(),
        command.recordingEnabled(),
        now,
        now);

    Meeting saved = meetingRepository.save(meeting);

    eventPublisher.publishEvent(new MeetingCreatedEvent(
        command.room().roomId(),
        saved.meetingId(),
        command.actorId(),
        command.traceId(),
        MEETING_ALL_FIELDS));

    return saved;
  }

  private void validateSchedule(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
      throw new InvalidMeetingScheduleException();
    }
  }

  private void validateRoomIsActive(Room room) {
    if (room.status() != RoomStatus.ACTIVE) {
      throw new MeetingRoomInactiveException(room.roomId());
    }
  }

  private void validateRoomConfigSet(String configSetId) {
    if (!configSetValidator.isValid(configSetId)) {
      throw new ConfigSetInvalidException(configSetId);
    }
  }

  private String normalizeRequired(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new InvalidMeetingDataException(message);
    }
    return value.trim();
  }

  private String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
