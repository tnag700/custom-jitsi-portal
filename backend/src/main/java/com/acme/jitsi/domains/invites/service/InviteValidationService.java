package com.acme.jitsi.domains.invites.service;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

@Service
@Conditional(NotDatabaseInviteModeCondition.class)
public class InviteValidationService implements InviteValidationPort {

  public record InviteResolution(String meetingId) {
  }

  public record InviteReservation(String inviteToken, String meetingId) {
  }

  private final InviteExchangeProperties properties;
  private final InviteUsageStoreRouter inviteUsageStoreRouter;
  private final InviteValidationChain inviteValidationChain;

  InviteValidationService(
      InviteExchangeProperties properties,
      InviteUsageStoreRouter inviteUsageStoreRouter,
      InviteValidationChain inviteValidationChain) {
    this.properties = properties;
    this.inviteUsageStoreRouter = inviteUsageStoreRouter;
    this.inviteValidationChain = inviteValidationChain;
  }

  @Override
  public InviteResolution validate(String inviteToken) {
    InviteValidationContext context = new InviteValidationContext(inviteToken, properties, inviteUsageStoreRouter);
    inviteValidationChain.validate(context);
    InviteExchangeProperties.Invite invite = context.invite();
    return new InviteResolution(invite.meetingId());
  }

  @Override
  public InviteResolution validateAndConsume(String inviteToken) {
    InviteReservation reservation = reserve(inviteToken);
    return new InviteResolution(reservation.meetingId());
  }

  @Override
  public InviteReservation reserve(String inviteToken) {
    InviteValidationContext context = new InviteValidationContext(inviteToken, properties, inviteUsageStoreRouter);
    inviteValidationChain.validate(context);

    InviteExchangeProperties.Invite invite = context.invite();
    inviteUsageStoreRouter.consume(invite);
    return new InviteReservation(invite.token(), invite.meetingId());
  }

  @Override
  public void rollback(InviteReservation reservation) {
    if (reservation == null) {
      return;
    }
    inviteUsageStoreRouter.rollback(reservation.inviteToken());
  }
}