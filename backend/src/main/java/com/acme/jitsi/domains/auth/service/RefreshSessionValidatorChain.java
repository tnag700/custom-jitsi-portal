package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.ErrorCode;
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
          ErrorCode.TOKEN_REVOKED.code(),
          knownState.tokenId(),
          knownState.subject(),
          knownState.meetingId());
      throw new AuthTokenException(HttpStatus.FORBIDDEN, ErrorCode.TOKEN_REVOKED.code(), "Сессия отозвана. Выполните вход через SSO.");
    }

    if (now.isAfter(knownState.absoluteExpiresAt()) || now.isAfter(knownState.idleExpiresAt())) {
      securityEventPublisher.publish(
          "REFRESH_EXPIRED",
          ErrorCode.AUTH_REQUIRED.code(),
          knownState.tokenId(),
          knownState.subject(),
          knownState.meetingId());
      throw new AuthTokenException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED.code(), "Сессия истекла. Выполните вход через SSO.");
    }
  }

  RefreshTokenStore.RefreshTokenState requireConsumable(
      RefreshTokenStore.ConsumeResult consumeResult,
      RefreshTokenPayload parsedToken) {
    if (consumeResult.status() == RefreshTokenStore.ConsumeStatus.USED) {
      securityEventPublisher.publish(
          "REFRESH_REUSE",
          ErrorCode.REFRESH_REUSE_DETECTED.code(),
          parsedToken.tokenId(),
          parsedToken.subject(),
          parsedToken.meetingId());
      throw new AuthTokenException(HttpStatus.CONFLICT, ErrorCode.REFRESH_REUSE_DETECTED.code(), "Обнаружено повторное использование refresh-токена.");
    }

    if (consumeResult.status() == RefreshTokenStore.ConsumeStatus.REVOKED) {
      securityEventPublisher.publish(
          "REFRESH_REVOKED",
          ErrorCode.TOKEN_REVOKED.code(),
          parsedToken.tokenId(),
          parsedToken.subject(),
          parsedToken.meetingId());
      throw new AuthTokenException(HttpStatus.FORBIDDEN, ErrorCode.TOKEN_REVOKED.code(), "Сессия отозвана. Выполните вход через SSO.");
    }

    if (consumeResult.status() == RefreshTokenStore.ConsumeStatus.MISSING || consumeResult.state() == null) {
      throw new AuthTokenException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_REQUIRED.code(), "Сессия отсутствует. Выполните вход через SSO.");
    }

    return consumeResult.state();
  }
}