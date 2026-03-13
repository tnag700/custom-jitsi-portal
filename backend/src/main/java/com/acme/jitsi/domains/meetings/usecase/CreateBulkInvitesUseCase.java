package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingBulkInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import com.acme.jitsi.domains.meetings.service.BulkInviteSummary;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBulkInvitesUseCase implements UseCase<CreateBulkInvitesCommand, BulkInviteResult> {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingRepository meetingRepository;
  private final MeetingStateGuard meetingStateGuard;
  private final ApplicationEventPublisher eventPublisher;
  private final BulkInviteRequestPreprocessor requestPreprocessor;
  private final BulkInviteRecipientProcessor recipientProcessor;

  public CreateBulkInvitesUseCase(
      MeetingInviteRepository inviteRepository,
      MeetingRepository meetingRepository,
      MeetingStateGuard meetingStateGuard,
      ApplicationEventPublisher eventPublisher,
      BulkInviteRequestPreprocessor requestPreprocessor,
      BulkInviteRecipientProcessor recipientProcessor) {
    this.inviteRepository = inviteRepository;
    this.meetingRepository = meetingRepository;
    this.meetingStateGuard = meetingStateGuard;
    this.eventPublisher = eventPublisher;
    this.requestPreprocessor = requestPreprocessor;
    this.recipientProcessor = recipientProcessor;
  }

  @Override
  @Transactional
  public BulkInviteResult execute(CreateBulkInvitesCommand command) {
    String meetingId = command.meetingId();
    Meeting meeting = loadMeetingForInviteCreation(meetingId);
    PreparedBulkInviteRequest preparedRequest = requestPreprocessor.prepare(command.request());
    BulkInviteRecipientProcessor.ProcessingState processing =
      recipientProcessor.process(command, preparedRequest);

    persistChanges(processing);

    BulkInviteResult result = new BulkInviteResult(
      List.copyOf(processing.created()),
      List.copyOf(processing.skipped()),
      List.copyOf(processing.errors()),
      new BulkInviteSummary(
        preparedRequest.recipients().size(),
        processing.created().size(),
        processing.skipped().size(),
        processing.errors().size()));

    eventPublisher.publishEvent(new MeetingBulkInviteCreatedEvent(
        meetingId,
        meeting.roomId(),
        command.actorId(),
        command.traceId(),
      "total="
        + preparedRequest.recipients().size()
        + ",success="
        + processing.created().size()
        + ",failed="
        + processing.errors().size()
        + ",skipped="
        + processing.skipped().size()
    ));

    return result;
  }

  private Meeting loadMeetingForInviteCreation(String meetingId) {
    Meeting meeting = meetingRepository.findById(meetingId)
        .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    meetingStateGuard.assertJoinAllowed(meeting);
    return meeting;
  }

  private void persistChanges(BulkInviteRecipientProcessor.ProcessingState processing) {
    if (!processing.invitesToRevoke().isEmpty()) {
      inviteRepository.saveAll(processing.invitesToRevoke());
    }
    if (!processing.invitesToSave().isEmpty()) {
      inviteRepository.saveAll(processing.invitesToSave());
    }
  }
}
