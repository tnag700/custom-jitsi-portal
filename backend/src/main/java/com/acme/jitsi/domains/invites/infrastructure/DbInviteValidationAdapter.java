package com.acme.jitsi.domains.invites.infrastructure;

import com.acme.jitsi.domains.invites.service.DatabaseInviteModeCondition;
import com.acme.jitsi.domains.invites.service.InviteExchangeException;
import com.acme.jitsi.domains.invites.service.InviteMeetingStatePort;
import com.acme.jitsi.domains.invites.service.InviteReservation;
import com.acme.jitsi.domains.invites.service.InviteValidationPort;
import com.acme.jitsi.domains.invites.service.InviteValidationResult;
import com.acme.jitsi.domains.meetings.service.InviteExhaustedException;
import com.acme.jitsi.domains.meetings.service.InviteExpiredException;
import com.acme.jitsi.domains.meetings.service.InviteNotFoundException;
import com.acme.jitsi.domains.meetings.service.InviteRevokedException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteService;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteCommand;
import com.acme.jitsi.domains.meetings.usecase.ConsumeInviteUseCase;
import com.acme.jitsi.shared.ErrorCode;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
  private final InviteMeetingStatePort inviteMeetingStatePort;
  private final ReservationRegistry reservationRegistry = new ReservationRegistry();

  public DbInviteValidationAdapter(
      MeetingInviteService inviteService,
      ConsumeInviteUseCase consumeInviteUseCase,
      InviteMeetingStatePort inviteMeetingStatePort) {
    this.inviteService = inviteService;
    this.consumeInviteUseCase = consumeInviteUseCase;
    this.inviteMeetingStatePort = inviteMeetingStatePort;
  }

  @Override
  public InviteValidationResult validate(String inviteToken) {
    MeetingInvite invite = inviteService.findByToken(inviteToken)
        .orElseThrow(() -> new InviteExchangeException(
            HttpStatus.NOT_FOUND,
            ErrorCode.INVITE_NOT_FOUND.code(),
            "Инвайт не найден."));

    if (invite.isRevoked()) {
      throw new InviteExchangeException(
          HttpStatus.GONE,
          ErrorCode.INVITE_REVOKED.code(),
          "Инвайт отозван.");
    }

    if (invite.isExpired()) {
      throw new InviteExchangeException(
          HttpStatus.GONE,
          ErrorCode.INVITE_EXPIRED.code(),
          "Срок действия инвайта истёк.");
    }

    inviteMeetingStatePort.assertJoinAllowed(invite.meetingId());

    if (invite.isExhausted()) {
      throw new InviteExchangeException(
          HttpStatus.CONFLICT,
          ErrorCode.INVITE_EXHAUSTED.code(),
          "Лимит использований инвайта исчерпан.");
    }

    return new InviteValidationResult(invite.meetingId());
  }

  @Override
  public InviteReservation reserve(String inviteToken) {
    MeetingInvite invite = consume(inviteToken);
    return reservationRegistry.issue(invite.token(), invite.meetingId());
  }

  @Override
  public void rollback(InviteReservation reservation) {
    if (!reservationRegistry.authorizeRollback(reservation)) {
      return;
    }
    inviteService.rollbackConsume(reservation.inviteToken());
  }

  private MeetingInvite consume(String inviteToken) {
    try {
      return consumeInviteUseCase.execute(new ConsumeInviteCommand(inviteToken));
    } catch (InviteNotFoundException ex) {
      throw new InviteExchangeException(HttpStatus.NOT_FOUND, ErrorCode.INVITE_NOT_FOUND.code(), "Инвайт не найден.", ex);
    } catch (InviteRevokedException ex) {
      throw new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_REVOKED.code(), "Инвайт отозван.", ex);
    } catch (InviteExpiredException ex) {
      throw new InviteExchangeException(HttpStatus.GONE, ErrorCode.INVITE_EXPIRED.code(), "Срок действия инвайта истёк.", ex);
    } catch (InviteExhaustedException ex) {
      throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.INVITE_EXHAUSTED.code(), "Лимит использований инвайта исчерпан.", ex);
    }
  }

  private static final class ReservationRegistry {

    private final Map<String, InviteReservation> activeReservations = new ConcurrentHashMap<>();

    InviteReservation issue(String inviteToken, String meetingId) {
      String reservationId = UUID.randomUUID().toString();
      InviteReservation reservation = InviteReservation.issue(reservationId, inviteToken, meetingId);
      activeReservations.put(reservationId, reservation);
      return reservation;
    }

    boolean authorizeRollback(InviteReservation reservation) {
      if (reservation == null) {
        return false;
      }
      return activeReservations.remove(reservation.reservationId(), reservation);
    }
  }
}