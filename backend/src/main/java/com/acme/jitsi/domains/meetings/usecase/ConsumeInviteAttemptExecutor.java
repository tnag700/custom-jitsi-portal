package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteExpiredException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.InviteRevokedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumeInviteAttemptExecutor {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingStateGuard meetingStateGuard;
  private final Clock clock;

  @Autowired
  public ConsumeInviteAttemptExecutor(
      MeetingInviteRepository inviteRepository,
      MeetingStateGuard meetingStateGuard,
      Clock clock) {
    this.inviteRepository = inviteRepository;
    this.meetingStateGuard = meetingStateGuard;
    this.clock = clock;
  }

  public ConsumeInviteAttemptExecutor(
      MeetingInviteRepository inviteRepository,
      MeetingStateGuard meetingStateGuard) {
    this(inviteRepository, meetingStateGuard, Clock.systemUTC());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MeetingInvite execute(ConsumeInviteCommand command) {
    String token = command.token();
    MeetingInvite invite = inviteRepository.findByToken(token)
        .orElseThrow(() -> new InviteNotFoundException(token));

    if (invite.isRevoked()) {
      throw new InviteRevokedException(token);
    }

    if (invite.isExpired(clock.instant())) {
      throw new InviteExpiredException(token);
    }

    meetingStateGuard.assertGuestJoinAllowed(invite.meetingId());

    if (invite.isExhausted()) {
      throw new InviteExhaustedException(token);
    }

    MeetingInvite updated = invite.withUsedCount(invite.usedCount() + 1);
    try {
      return inviteRepository.save(updated);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new RetryableConsumeInviteContentionException(token, e);
    }
  }
}
