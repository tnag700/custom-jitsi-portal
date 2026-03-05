package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingCanceledEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingFinalizedException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelMeetingUseCase implements UseCase<CancelMeetingCommand, Meeting> {

  private static final String MEETING_STATUS_FIELD = "status";

  private final MeetingRepository meetingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public CancelMeetingUseCase(
      MeetingRepository meetingRepository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.meetingRepository = meetingRepository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public Meeting execute(CancelMeetingCommand command) {
    Meeting existing = command.existing();
    assertEditable(existing);

    Meeting saved = meetingRepository.save(existing.withStatus(MeetingStatus.CANCELED, Instant.now(clock)));

    eventPublisher.publishEvent(new MeetingCanceledEvent(
        saved.roomId(),
        saved.meetingId(),
        command.actorId(),
        command.traceId(),
        MEETING_STATUS_FIELD));

    return saved;
  }

  private void assertEditable(Meeting meeting) {
    if (meeting.status() != MeetingStatus.SCHEDULED) {
      throw new MeetingFinalizedException(meeting.meetingId());
    }
  }
}
