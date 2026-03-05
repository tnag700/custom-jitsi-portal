package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteExpiredException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.InviteRevokedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumeInviteUseCase implements UseCase<ConsumeInviteCommand, MeetingInvite> {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingStateGuard meetingStateGuard;

  public ConsumeInviteUseCase(
      MeetingInviteRepository inviteRepository,
      MeetingStateGuard meetingStateGuard) {
    this.inviteRepository = inviteRepository;
    this.meetingStateGuard = meetingStateGuard;
  }

  @Override
  // IMPORTANT: @Retryable (order=0, outer) must wrap @Transactional (order=MAX, inner) so each
  // retry attempt starts a fresh transaction — required for OCC retry semantics.
  // Do NOT change AOP proxy ordering without careful testing.
  @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100))
  @Transactional
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

    if (invite.isExhausted()) {
      throw new InviteExhaustedException(token);
    }

    meetingStateGuard.assertJoinAllowed(invite.meetingId());

    MeetingInvite updated = invite.withUsedCount(invite.usedCount() + 1);
    try {
      return inviteRepository.save(updated);
    } catch (ObjectOptimisticLockingFailureException e) {
      throw new InviteExhaustedException(token, e);
    }
  }
}
