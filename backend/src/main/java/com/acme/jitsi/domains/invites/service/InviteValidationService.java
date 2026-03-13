package com.acme.jitsi.domains.invites.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

@Service
@Conditional(NotDatabaseInviteModeCondition.class)
public class InviteValidationService implements InviteValidationPort {

  private final InviteExchangeProperties properties;
  private final InviteUsageStoreRouter inviteUsageStoreRouter;
  private final InviteValidationChain inviteValidationChain;
  private final InviteReservationRegistry reservationRegistry = new InviteReservationRegistry();

  InviteValidationService(
      InviteExchangeProperties properties,
      InviteUsageStoreRouter inviteUsageStoreRouter,
      InviteValidationChain inviteValidationChain) {
    this.properties = properties;
    this.inviteUsageStoreRouter = inviteUsageStoreRouter;
    this.inviteValidationChain = inviteValidationChain;
  }

  @Override
  public InviteValidationResult validate(String inviteToken) {
    InviteValidationContext context = new InviteValidationContext(inviteToken, properties, inviteUsageStoreRouter);
    inviteValidationChain.validate(context);
    InviteExchangeProperties.Invite invite = context.invite();
    return new InviteValidationResult(invite.meetingId());
  }

  @Override
  public InviteReservation reserve(String inviteToken) {
    InviteValidationContext context = new InviteValidationContext(inviteToken, properties, inviteUsageStoreRouter);
    inviteValidationChain.validate(context);

    InviteExchangeProperties.Invite invite = context.invite();
    inviteUsageStoreRouter.consume(invite);
    return reservationRegistry.issue(invite.token(), invite.meetingId());
  }

  @Override
  public void rollback(InviteReservation reservation) {
    if (!reservationRegistry.authorizeRollback(reservation)) {
      return;
    }
    inviteUsageStoreRouter.rollback(reservation.inviteToken());
  }
}