package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
public class ConsumeInviteUseCase implements UseCase<ConsumeInviteCommand, MeetingInvite> {

  private final ConsumeInviteConcurrencyBoundary concurrencyBoundary;

  public ConsumeInviteUseCase(ConsumeInviteConcurrencyBoundary concurrencyBoundary) {
    this.concurrencyBoundary = concurrencyBoundary;
  }

  @Override
  public MeetingInvite execute(ConsumeInviteCommand command) {
    try {
      return concurrencyBoundary.execute(command);
    } catch (RetryableConsumeInviteContentionException exception) {
      Throwable cause = exception.getCause();
      throw new InviteExhaustedException(command.token(), cause == null ? exception : cause);
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new InviteExhaustedException(command.token(), exception);
    }
  }
}
