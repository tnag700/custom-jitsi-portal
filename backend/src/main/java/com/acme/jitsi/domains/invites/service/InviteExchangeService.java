package com.acme.jitsi.domains.invites.service;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.validation.TextInputNormalizer;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InviteExchangeService {

  public record ExchangeResult(String joinUrl, Instant expiresAt, String role, String meetingId) {
  }

  public record ValidationResult(String meetingId) {
  }

  private final InviteValidationCapability inviteValidationCapability;
  private final InviteReservationCapability inviteReservationCapability;
  private final InviteJoinPort inviteJoinPort;
  private final FlowObservationFacade flowObservationFacade;

  InviteExchangeService(
      InviteValidationCapability inviteValidationCapability,
      InviteReservationCapability inviteReservationCapability,
      InviteJoinPort inviteJoinPort) {
    this(
        inviteValidationCapability,
        inviteReservationCapability,
        inviteJoinPort,
        FlowObservationFacade.noop());
  }

  @Autowired
  InviteExchangeService(
      InviteValidationCapability inviteValidationCapability,
      InviteReservationCapability inviteReservationCapability,
      InviteJoinPort inviteJoinPort,
      FlowObservationFacade flowObservationFacade) {
    this.inviteValidationCapability = inviteValidationCapability;
    this.inviteReservationCapability = inviteReservationCapability;
    this.inviteJoinPort = inviteJoinPort;
    this.flowObservationFacade = flowObservationFacade;
  }

  public ExchangeResult exchange(String inviteToken, String displayName) {
    return flowObservationFacade.observe("invite.exchange", observation -> {
      observation.guest(true).rollback("skipped");

      InviteReservation reservation;
      try {
        observation.stage("validate");
        reservation = inviteReservationCapability.reserve(inviteToken);
      } catch (RuntimeException ex) {
        classifyReservationFailure(observation, ex);
        throw ex;
      }

      String guestSubject = buildGuestSubject(displayName);

      InviteJoinPort.JoinResult tokenResult;
      try {
        observation.stage("issue_token");
        tokenResult = inviteJoinPort.issueGuestJoin(reservation.meetingId(), guestSubject);
        observation.outcome("success");
      } catch (RuntimeException ex) {
        observation.outcome("partial_failure").stage("issue_token");
        try {
          inviteReservationCapability.rollback(reservation);
          observation.rollback("performed");
        } catch (RuntimeException rollbackEx) {
          observation.rollback("failed");
          ex.addSuppressed(rollbackEx);
        }
        throw ex;
      }

      return new ExchangeResult(
          tokenResult.joinUrl(),
          tokenResult.expiresAt(),
          tokenResult.role(),
          reservation.meetingId());
    });
  }

  public ValidationResult validate(String inviteToken) {
    InviteValidationResult resolution = inviteValidationCapability.validate(inviteToken);
    return new ValidationResult(resolution.meetingId());
  }

  private String buildGuestSubject(String displayName) {
    String normalizedDisplayName = normalizeGuestDisplayName(displayName);
    if (normalizedDisplayName == null) {
      return "guest:" + UUID.randomUUID();
    }
    return "guest:"
        + normalizedDisplayName.replaceAll("\\s+", "-").toLowerCase(Locale.ROOT)
        + ":"
        + UUID.randomUUID();
  }

  private String normalizeGuestDisplayName(String displayName) {
    String normalized = TextInputNormalizer.normalizeNullable(displayName);
    if (normalized == null) {
      return null;
    }
    if (normalized.isEmpty() || normalized.length() < 2 || normalized.length() > 80) {
      throw new InviteExchangeException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.INVALID_INVITE.code(),
          "displayName must be between 2 and 80 characters.");
    }
    if (TextInputNormalizer.containsControlCharacters(normalized)) {
      throw new InviteExchangeException(
          HttpStatus.BAD_REQUEST,
          ErrorCode.INVALID_INVITE.code(),
          "displayName must not contain control characters.");
    }
    return normalized;
  }

  private void classifyReservationFailure(FlowObservationFacade.FlowObservation observation, RuntimeException ex) {
    observation.rollback("skipped");
    if (ex instanceof InviteExchangeException inviteExchangeException) {
      if (ErrorCode.INVITE_EXHAUSTED.code().equals(inviteExchangeException.errorCode())) {
        observation.outcome("contention").stage("reserve");
        return;
      }
      observation.outcome("validation_failure").stage("validate");
      return;
    }
    observation.outcome("partial_failure").stage("reserve");
  }
}