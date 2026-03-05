package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class RefreshSessionValidatorChainTest {

  @Test
  void rejectsRevokedKnownStateAndPublishesRevokedEvent() {
    RefreshSecurityEventPublisher securityEventPublisher = mock(RefreshSecurityEventPublisher.class);
    RefreshSessionValidatorChain chain = new RefreshSessionValidatorChain(securityEventPublisher);

    RefreshTokenStore.RefreshTokenState revokedState = new RefreshTokenStore.RefreshTokenState(
        "revoked-jti-1",
        "u-host",
        "meeting-a",
        Instant.now().plus(1, ChronoUnit.HOURS),
        Instant.now().plus(1, ChronoUnit.HOURS),
        RefreshTokenStore.TokenStatus.REVOKED);

    assertThatThrownBy(() -> chain.validateKnownState(revokedState, Instant.now()))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(exception.errorCode()).isEqualTo("TOKEN_REVOKED");
        });

    verify(securityEventPublisher).publish("REFRESH_REVOKED", "TOKEN_REVOKED", "revoked-jti-1", "u-host", "meeting-a");
  }

  @Test
  void rejectsExpiredKnownStateAndPublishesExpiredEvent() {
    RefreshSecurityEventPublisher securityEventPublisher = mock(RefreshSecurityEventPublisher.class);
    RefreshSessionValidatorChain chain = new RefreshSessionValidatorChain(securityEventPublisher);

    RefreshTokenStore.RefreshTokenState expiredState = new RefreshTokenStore.RefreshTokenState(
        "expired-jti-1",
        "u-host",
        "meeting-a",
        Instant.now().plus(1, ChronoUnit.HOURS),
        Instant.now().minus(1, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.ACTIVE);

    assertThatThrownBy(() -> chain.validateKnownState(expiredState, Instant.now()))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("AUTH_REQUIRED");

    verify(securityEventPublisher).publish("REFRESH_EXPIRED", "AUTH_REQUIRED", "expired-jti-1", "u-host", "meeting-a");
  }

  @Test
  void rejectsReuseConsumeResultAndPublishesReuseEvent() {
    RefreshSecurityEventPublisher securityEventPublisher = mock(RefreshSecurityEventPublisher.class);
    RefreshSessionValidatorChain chain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshTokenPayload payload = new RefreshTokenPayload(
        "reuse-jti-1",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    RefreshTokenStore.RefreshTokenState activeState = new RefreshTokenStore.RefreshTokenState(
        payload.tokenId(),
        payload.subject(),
        payload.meetingId(),
        payload.expiresAt(),
        Instant.now().plus(1, ChronoUnit.HOURS),
        RefreshTokenStore.TokenStatus.ACTIVE);

    RefreshTokenStore.ConsumeResult consumeResult = new RefreshTokenStore.ConsumeResult(RefreshTokenStore.ConsumeStatus.USED, activeState);

    assertThatThrownBy(() -> chain.requireConsumable(consumeResult, payload))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("REFRESH_REUSE_DETECTED");

    verify(securityEventPublisher).publish("REFRESH_REUSE", "REFRESH_REUSE_DETECTED", "reuse-jti-1", "u-host", "meeting-a");
  }

  @Test
  void rejectsRevokedConsumeResultAndPublishesRevokedEvent() {
    RefreshSecurityEventPublisher securityEventPublisher = mock(RefreshSecurityEventPublisher.class);
    RefreshSessionValidatorChain chain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshTokenPayload payload = new RefreshTokenPayload(
        "revoked-consume-jti-1",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    RefreshTokenStore.RefreshTokenState state = new RefreshTokenStore.RefreshTokenState(
        payload.tokenId(),
        payload.subject(),
        payload.meetingId(),
        payload.expiresAt(),
        Instant.now().plus(1, ChronoUnit.HOURS),
        RefreshTokenStore.TokenStatus.REVOKED);

    RefreshTokenStore.ConsumeResult consumeResult = new RefreshTokenStore.ConsumeResult(RefreshTokenStore.ConsumeStatus.REVOKED, state);

    assertThatThrownBy(() -> chain.requireConsumable(consumeResult, payload))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(exception.errorCode()).isEqualTo("TOKEN_REVOKED");
        });

    verify(securityEventPublisher).publish("REFRESH_REVOKED", "TOKEN_REVOKED", "revoked-consume-jti-1", "u-host", "meeting-a");
  }

  @Test
  void rejectsMissingConsumeResultWithAuthRequiredWithoutSecurityEvent() {
    RefreshSecurityEventPublisher securityEventPublisher = mock(RefreshSecurityEventPublisher.class);
    RefreshSessionValidatorChain chain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshTokenPayload payload = new RefreshTokenPayload(
        "missing-jti-1",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    RefreshTokenStore.ConsumeResult consumeResult = new RefreshTokenStore.ConsumeResult(RefreshTokenStore.ConsumeStatus.MISSING, null);

    assertThatThrownBy(() -> chain.requireConsumable(consumeResult, payload))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("AUTH_REQUIRED");

    verify(securityEventPublisher, never()).publish(anyString(), anyString(), anyString(), anyString(), anyString());
  }
}