package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateInviteUseCase implements UseCase<CreateInviteCommand, MeetingInvite> {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingRepository meetingRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final SecureInviteTokenGenerator tokenGenerator;
  private final Clock clock;

  public CreateInviteUseCase(
      MeetingInviteRepository inviteRepository,
      MeetingRepository meetingRepository,
      ApplicationEventPublisher eventPublisher,
      SecureInviteTokenGenerator tokenGenerator,
      Clock clock) {
    this.inviteRepository = inviteRepository;
    this.meetingRepository = meetingRepository;
    this.eventPublisher = eventPublisher;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public MeetingInvite execute(CreateInviteCommand command) {
    Meeting meeting = meetingRepository.findById(command.meetingId())
        .orElseThrow(() -> new MeetingNotFoundException(command.meetingId()));

    validateRole(command.role());
    int uses = resolveUses(command.maxUses());
    validateExpiresAt(command.expiresAt());

    MeetingInvite saved = inviteRepository.save(new MeetingInvite(
        UUID.randomUUID().toString(),
        command.meetingId(),
        tokenGenerator.generateToken(),
        command.role(),
        uses,
        0,
        command.expiresAt(),
        null,
        Instant.now(clock),
        command.actorId(),
        null,
        null,
        0L));

    eventPublisher.publishEvent(new MeetingInviteCreatedEvent(
        command.meetingId(),
        meeting.roomId(),
        command.actorId(),
        command.traceId(),
        "role=" + command.role().value() + ",maxUses=" + uses + ",expiresAt=" + command.expiresAt()
    ));

    return saved;
  }

  private void validateRole(MeetingRole role) {
    if (role == MeetingRole.HOST) {
      throw new IllegalArgumentException("Cannot create invite with HOST role");
    }
  }

  private int resolveUses(Integer maxUses) {
    if (maxUses == null) {
      return 1;
    }
    if (maxUses < 1) {
      throw new IllegalArgumentException("maxUses must be at least 1");
    }
    return maxUses;
  }

  private void validateExpiresAt(Instant expiresAt) {
    if (expiresAt == null) {
      return;
    }
    Instant maxExpiry = Instant.now(clock).plusSeconds(7L * 24 * 60 * 60);
    if (expiresAt.isAfter(maxExpiry)) {
      throw new IllegalArgumentException("expiresInHours cannot exceed 168 (7 days)");
    }
  }
}
