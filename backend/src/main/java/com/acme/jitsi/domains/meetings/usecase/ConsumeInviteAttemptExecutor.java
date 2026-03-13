package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteExpiredException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.InviteRevokedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumeInviteAttemptExecutor {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingStateGuard meetingStateGuard;

  public ConsumeInviteAttemptExecutor(
      MeetingInviteRepository inviteRepository,
      MeetingStateGuard meetingStateGuard) {
    this.inviteRepository = inviteRepository;
    this.meetingStateGuard = meetingStateGuard;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public MeetingInvite execute(ConsumeInviteCommand command) {
    String token = command.token();
    MeetingInvite invite = inviteRepository.findByToken(token)
        .orElseThrow(() -> new InviteNotFoundException(token));

    if (invite.isRevoked()) {
      throw new InviteRevokedException(token);
    }

    if (invite.isExpired()) {
      throw new InviteExpiredException(token);
    }

    meetingStateGuard.assertJoinAllowed(invite.meetingId());

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