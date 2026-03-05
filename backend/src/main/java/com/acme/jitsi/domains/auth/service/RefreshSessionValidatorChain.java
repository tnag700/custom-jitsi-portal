package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class RefreshSessionValidatorChain {

  private final RefreshSecurityEventPublisher securityEventPublisher;

  RefreshSessionValidatorChain(RefreshSecurityEventPublisher securityEventPublisher) {
    this.securityEventPublisher = securityEventPublisher;
  }

  void validateKnownState(RefreshTokenStore.RefreshTokenState knownState, Instant now) {
    if (knownState.status() == RefreshTokenStore.TokenStatus.REVOKED) {
      securityEventPublisher.publish(
          "REFRESH_REVOKED",
          "TOKEN_REVOKED",
          knownState.tokenId(),
          knownState.subject(),
          knownState.meetingId());
      throw new MeetingTokenException(HttpStatus.FORBIDDEN, "TOKEN_REVOKED", "Сессия отозвана. Выполните вход через SSO.");
    }

    if (now.isAfter(knownState.absoluteExpiresAt()) || now.isAfter(knownState.idleExpiresAt())) {
      securityEventPublisher.publish(
          "REFRESH_EXPIRED",
          "AUTH_REQUIRED",
          knownState.tokenId(),
          knownState.subject(),
          knownState.meetingId());
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Сессия истекла. Выполните вход через SSO.");
    }
  }

  RefreshTokenStore.RefreshTokenState requireConsumable(
      RefreshTokenStore.ConsumeResult consumeResult,
      RefreshTokenPayload parsedToken) {
    if (consumeResult.status() == RefreshTokenStore.ConsumeStatus.USED) {
      securityEventPublisher.publish(
          "REFRESH_REUSE",
          "REFRESH_REUSE_DETECTED",
          parsedToken.tokenId(),
          parsedToken.subject(),
          parsedToken.meetingId());
      throw new MeetingTokenException(HttpStatus.CONFLICT, "REFRESH_REUSE_DETECTED", "Обнаружено повторное использование refresh-токена.");
    }

    if (consumeResult.status() == RefreshTokenStore.ConsumeStatus.REVOKED) {
      securityEventPublisher.publish(
          "REFRESH_REVOKED",
          "TOKEN_REVOKED",
          parsedToken.tokenId(),
          parsedToken.subject(),
          parsedToken.meetingId());
      throw new MeetingTokenException(HttpStatus.FORBIDDEN, "TOKEN_REVOKED", "Сессия отозвана. Выполните вход через SSO.");
    }

    if (consumeResult.status() == RefreshTokenStore.ConsumeStatus.MISSING || consumeResult.state() == null) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Сессия отсутствует. Выполните вход через SSO.");
    }

    return consumeResult.state();
  }
}