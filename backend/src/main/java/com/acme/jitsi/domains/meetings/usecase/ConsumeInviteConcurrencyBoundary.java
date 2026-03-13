package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class ConsumeInviteConcurrencyBoundary {

  private final ConsumeInviteAttemptExecutor attemptExecutor;

  public ConsumeInviteConcurrencyBoundary(ConsumeInviteAttemptExecutor attemptExecutor) {
    this.attemptExecutor = attemptExecutor;
  }

  @Retryable(
      retryFor = {
          ObjectOptimisticLockingFailureException.class,
          RetryableConsumeInviteContentionException.class
      },
      maxAttemptsExpression = "${app.resilience.meetings.consume-invite.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${app.resilience.meetings.consume-invite.retry.delay-ms:100}"))
  public MeetingInvite execute(ConsumeInviteCommand command) {
    return attemptExecutor.execute(command);
  }
}