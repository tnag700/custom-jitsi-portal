package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingInviteRevokedEvent;
import com.acme.jitsi.domains.meetings.service.InviteAlreadyRevokedException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RevokeInviteUseCase implements UseCase<RevokeInviteCommand, MeetingInvite> {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingRepository meetingRepository;
  private final ApplicationEventPublisher eventPublisher;

  public RevokeInviteUseCase(
      MeetingInviteRepository inviteRepository,
      MeetingRepository meetingRepository,
      ApplicationEventPublisher eventPublisher) {
    this.inviteRepository = inviteRepository;
    this.meetingRepository = meetingRepository;
    this.eventPublisher = eventPublisher;
  }

  @Override
  @Transactional
  public MeetingInvite execute(RevokeInviteCommand command) {
    MeetingInvite invite = inviteRepository.findById(command.inviteId())
        .orElseThrow(() -> new InviteNotFoundException(command.inviteId()));

    if (!invite.meetingId().equals(command.meetingId())) {
      throw new InviteNotFoundException(command.inviteId());
    }

    if (invite.isRevoked()) {
      throw new InviteAlreadyRevokedException(command.inviteId());
    }

    Meeting meeting = meetingRepository.findById(command.meetingId())
        .orElseThrow(() -> new MeetingNotFoundException(command.meetingId()));

    MeetingInvite revoked = invite.withRevoked();
    MeetingInvite saved = inviteRepository.save(revoked);

    eventPublisher.publishEvent(new MeetingInviteRevokedEvent(
        command.meetingId(),
        meeting.roomId(),
        command.actorId(),
        command.traceId(),
        "inviteId=" + command.inviteId()
    ));

    return saved;
  }
}
