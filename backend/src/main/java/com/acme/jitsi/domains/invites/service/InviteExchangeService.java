package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InviteExchangeService {

  public record ExchangeResult(String joinUrl, Instant expiresAt, String role, String meetingId) {
  }

  public record ValidationResult(String meetingId) {
  }

    private final InviteValidationPort inviteValidationService;
  private final MeetingTokenIssuer meetingAccessTokenService;

  InviteExchangeService(
      InviteValidationPort inviteValidationService,
      MeetingTokenIssuer meetingAccessTokenService) {
    this.inviteValidationService = inviteValidationService;
    this.meetingAccessTokenService = meetingAccessTokenService;
  }

  public ExchangeResult exchange(String inviteToken, String displayName) {
    InviteValidationService.InviteReservation reservation = inviteValidationService.reserve(inviteToken);
    String guestSubject = buildGuestSubject(displayName);

    MeetingTokenIssuer.TokenResult tokenResult;
    try {
      tokenResult = meetingAccessTokenService.issueGuestToken(reservation.meetingId(), guestSubject);
    } catch (RuntimeException ex) {
      inviteValidationService.rollback(reservation);
      throw ex;
    }

    return new ExchangeResult(
        tokenResult.joinUrl(),
        tokenResult.expiresAt(),
        tokenResult.role(),
        reservation.meetingId());
  }

  public ValidationResult validate(String inviteToken) {
    InviteValidationService.InviteResolution resolution = inviteValidationService.validate(inviteToken);
    return new ValidationResult(resolution.meetingId());
  }

  private String buildGuestSubject(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      return "guest:" + UUID.randomUUID();
    }
    return "guest:" + displayName.trim().replaceAll("\\s+", "-").toLowerCase(Locale.ROOT) + ":" + UUID.randomUUID();
  }
}