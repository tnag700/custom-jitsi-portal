package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteUseCase;
import com.acme.jitsi.domains.invites.service.InviteValidationService.InviteResolution;
import com.acme.jitsi.domains.invites.service.InviteValidationService.InviteReservation;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;


@Service
@Primary
@Conditional(DatabaseInviteModeCondition.class)
public class DbInviteValidationAdapter implements InviteValidationPort {

  private final MeetingInviteService inviteService;
  private final ConsumeInviteUseCase consumeInviteUseCase;
  private final MeetingStateGuard meetingStateGuard;

  public DbInviteValidationAdapter(
      MeetingInviteService inviteService,
      ConsumeInviteUseCase consumeInviteUseCase,
      MeetingStateGuard meetingStateGuard) {
    this.inviteService = inviteService;
    this.consumeInviteUseCase = consumeInviteUseCase;
    this.meetingStateGuard = meetingStateGuard;
  }

  @Override
  public InviteResolution validate(String inviteToken) {
    MeetingInvite invite = inviteService.findByToken(inviteToken)
        .orElseThrow(() -> new com.acme.jitsi.domains.meetings.service.MeetingTokenException(
            HttpStatus.NOT_FOUND,
            "INVITE_NOT_FOUND",
            "Инвайт не найден."));

    if (invite.isRevoked()) {
      throw new com.acme.jitsi.domains.meetings.service.MeetingTokenException(
          HttpStatus.GONE,
          "INVITE_REVOKED",
          "Инвайт отозван.");
    }

    if (invite.isExpired()) {
      throw new com.acme.jitsi.domains.meetings.service.MeetingTokenException(
          HttpStatus.GONE,
          "INVITE_EXPIRED",
          "Срок действия инвайта истёк.");
    }

    if (invite.isExhausted()) {
      throw new com.acme.jitsi.domains.meetings.service.MeetingTokenException(
          HttpStatus.CONFLICT,
          "INVITE_EXHAUSTED",
          "Лимит использований инвайта исчерпан.");
    }

    meetingStateGuard.assertJoinAllowed(invite.meetingId());
    return new InviteResolution(invite.meetingId());
  }

  @Override
  public InviteResolution validateAndConsume(String inviteToken) {
    MeetingInvite invite = consumeInviteUseCase.execute(new ConsumeInviteCommand(inviteToken));
    return new InviteResolution(invite.meetingId());
  }

  @Override
  public InviteReservation reserve(String inviteToken) {
    MeetingInvite invite = consumeInviteUseCase.execute(new ConsumeInviteCommand(inviteToken));
    return new InviteReservation(invite.token(), invite.meetingId());
  }

  @Override
  public void rollback(InviteReservation reservation) {
    if (reservation == null) {
      return;
    }
    inviteService.rollbackConsume(reservation.inviteToken());
  }
}
