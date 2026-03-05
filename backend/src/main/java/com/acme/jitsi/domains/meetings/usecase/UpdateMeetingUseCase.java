package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingUpdatedEvent;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingDataException;
import com.acme.jitsi.domains.meetings.service.InvalidMeetingScheduleException;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingFinalizedException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateMeetingUseCase implements UseCase<UpdateMeetingCommand, Meeting> {

  private static final String MEETING_ALL_FIELDS =
      "title,description,meetingType,startsAt,endsAt,allowGuests,recordingEnabled";

  private final MeetingRepository meetingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public UpdateMeetingUseCase(
      MeetingRepository meetingRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.meetingRepository = meetingRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Meeting execute(UpdateMeetingCommand command) {
    Meeting existing = command.existing();
    assertEditable(existing);

    Instant nextStartsAt = command.startsAt() != null ? command.startsAt() : existing.startsAt();
    Instant nextEndsAt = command.endsAt() != null ? command.endsAt() : existing.endsAt();
    validateSchedule(nextStartsAt, nextEndsAt);

    Meeting updated = buildUpdatedMeeting(command, existing, nextStartsAt, nextEndsAt);

    Meeting saved = meetingRepository.save(updated);
    List<String> changed = detectChangedFields(existing, saved);

    eventPublisher.publishEvent(new MeetingUpdatedEvent(
        saved.roomId(),
        saved.meetingId(),
        command.actorId(),
        command.traceId(),
        changed.isEmpty() ? MEETING_ALL_FIELDS : String.join(",", changed)));

    return saved;
  }

  private Meeting buildUpdatedMeeting(
      UpdateMeetingCommand command,
      Meeting existing,
      Instant nextStartsAt,
      Instant nextEndsAt) {
    return new Meeting(
        existing.meetingId(),
        existing.roomId(),
        command.title() != null ? normalizeRequired(command.title(), "Meeting title must not be blank") : existing.title(),
        command.description() != null ? normalizeOptional(command.description()) : existing.description(),
        command.meetingType() != null ? normalizeRequired(command.meetingType(), "Meeting type must not be blank") : existing.meetingType(),
        existing.configSetId(),
        existing.status(),
        nextStartsAt,
        nextEndsAt,
        command.allowGuests() != null ? command.allowGuests() : existing.allowGuests(),
        command.recordingEnabled() != null ? command.recordingEnabled() : existing.recordingEnabled(),
        existing.createdAt(),
        Instant.now(clock));
  }

  private List<String> detectChangedFields(Meeting existing, Meeting saved) {
    List<String> changed = new ArrayList<>();
    if (!Objects.equals(existing.title(), saved.title())) changed.add("title");
    if (!Objects.equals(existing.description(), saved.description())) changed.add("description");
    if (!Objects.equals(existing.meetingType(), saved.meetingType())) changed.add("meetingType");
    if (!Objects.equals(existing.startsAt(), saved.startsAt())) changed.add("startsAt");
    if (!Objects.equals(existing.endsAt(), saved.endsAt())) changed.add("endsAt");
    if (existing.allowGuests() != saved.allowGuests()) changed.add("allowGuests");
    if (existing.recordingEnabled() != saved.recordingEnabled()) changed.add("recordingEnabled");
    return changed;
  }

  private void validateSchedule(Instant startsAt, Instant endsAt) {
    if (startsAt == null || endsAt == null || !startsAt.isBefore(endsAt)) {
      throw new InvalidMeetingScheduleException();
    }
  }

  private void assertEditable(Meeting meeting) {
    if (meeting.status() != MeetingStatus.SCHEDULED) {
      throw new MeetingFinalizedException(meeting.meetingId());
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
