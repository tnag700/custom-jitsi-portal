package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Conditional(NotDatabaseInviteModeCondition.class)
public class InviteValidationService implements InviteValidationPort {

  private final InviteExchangeProperties properties;
  private final InviteUsageStoreRouter inviteUsageStoreRouter;
  private final InviteMeetingStatePort inviteMeetingStatePort;
  private final InviteReservationRegistry reservationRegistry = new InviteReservationRegistry();

  InviteValidationService(
      InviteExchangeProperties properties,
      InviteUsageStoreRouter inviteUsageStoreRouter,
      InviteMeetingStatePort inviteMeetingStatePort) {
    this.properties = properties;
    this.inviteUsageStoreRouter = inviteUsageStoreRouter;
    this.inviteMeetingStatePort = inviteMeetingStatePort;
  }

  @Override
  public InviteValidationResult validate(String inviteToken) {
    return new InviteValidationResult(resolveAndValidate(inviteToken).meetingId());
  }

  @Override
  public InviteReservation reserve(String inviteToken) {
    InviteExchangeProperties.Invite invite = resolveAndValidate(inviteToken);
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

  @Override
  public void complete(InviteReservation reservation) {
    reservationRegistry.complete(reservation);
  }

  private InviteExchangeProperties.Invite resolveAndValidate(String inviteToken) {
    if (inviteToken == null || inviteToken.isBlank()) {
      throw new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code(), "Инвайт не найден.");
    }
    InviteExchangeProperties.Invite invite = properties.invites().stream()
        .filter(candidate -> inviteToken.equals(candidate.token()))
        .findFirst()
        .orElseThrow(() -> new InviteExchangeException(
            HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code(), "Инвайт не найден."));
    if (invite.revoked()) {
      throw new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_REVOKED.code(), "Инвайт отозван.");
    }
    Instant expiresAt = invite.expiresAt();
    if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
      throw new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_EXPIRED.code(), "Срок действия инвайта истек.");
    }

    assertMeetingJoinAllowed(invite.meetingId());
    inviteUsageStoreRouter.assertCanConsume(invite);
    return invite;
  }

  private void assertMeetingJoinAllowed(String meetingId) {
    if (!properties.knownMeetingIds().isEmpty() && !properties.knownMeetingIds().contains(meetingId)) {
      throw new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.MEETING_NOT_FOUND.code(), "Встреча не найдена.");
    }
    if (properties.canceledMeetingIds().contains(meetingId)) {
      throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_CANCELED.code(), "Встреча отменена.");
    }
    if (properties.closedMeetingIds().contains(meetingId)) {
      throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена.");
    }
    if (properties.knownMeetingIds().isEmpty() || !properties.knownMeetingIds().contains(meetingId)) {
      inviteMeetingStatePort.assertJoinAllowed(meetingId);
    }
  }
}
