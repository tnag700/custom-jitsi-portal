package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthRefreshService {

  public record RefreshResult(String accessToken, String refreshToken, Instant expiresAt, String role, String tokenType) {
  }

  private final AuthAccessTokenIssuer accessTokenIssuer;
  private final RefreshTokenStore refreshTokenStore;
  private final AuthRefreshProperties refreshProperties;
  private final RefreshTokenParser refreshTokenParser;
  private final RefreshSessionValidatorChain refreshSessionValidatorChain;
  private final RefreshRotationService refreshRotationService;
  private final RefreshSecurityEventPublisher securityEventPublisher;
  private final FlowObservationFacade flowObservationFacade;

  AuthRefreshService(
      AuthAccessTokenIssuer accessTokenIssuer,
      RefreshTokenStore refreshTokenStore,
      AuthRefreshProperties refreshProperties,
      RefreshTokenParser refreshTokenParser,
      RefreshSessionValidatorChain refreshSessionValidatorChain,
      RefreshRotationService refreshRotationService,
      RefreshSecurityEventPublisher securityEventPublisher) {
    this(
        accessTokenIssuer,
        refreshTokenStore,
        refreshProperties,
        refreshTokenParser,
        refreshSessionValidatorChain,
        refreshRotationService,
        securityEventPublisher,
        FlowObservationFacade.noop());
  }

  @Autowired
  AuthRefreshService(
      AuthAccessTokenIssuer accessTokenIssuer,
      RefreshTokenStore refreshTokenStore,
      AuthRefreshProperties refreshProperties,
      RefreshTokenParser refreshTokenParser,
      RefreshSessionValidatorChain refreshSessionValidatorChain,
      RefreshRotationService refreshRotationService,
      RefreshSecurityEventPublisher securityEventPublisher,
      FlowObservationFacade flowObservationFacade) {
    this.accessTokenIssuer = accessTokenIssuer;
    this.refreshTokenStore = refreshTokenStore;
    this.refreshProperties = refreshProperties;
    this.refreshTokenParser = refreshTokenParser;
    this.refreshSessionValidatorChain = refreshSessionValidatorChain;
    this.refreshRotationService = refreshRotationService;
    this.securityEventPublisher = securityEventPublisher;
    this.flowObservationFacade = flowObservationFacade;

    if (refreshProperties.revokedTokenIds() != null) {
      refreshProperties.revokedTokenIds().stream()
          .filter(tokenId -> tokenId != null && !tokenId.isBlank())
          .forEach(refreshTokenStore::revoke);
    }
  }

  public void revoke(String tokenId) {
    if (tokenId == null || tokenId.isBlank()) {
      throw new AuthTokenException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_REQUEST.code(), "Требуется идентификатор refresh-токена.");
    }

    refreshTokenStore.revoke(tokenId);
    securityEventPublisher.publish("REFRESH_REVOKED", ErrorCode.TOKEN_REVOKED.code(), tokenId, "", "");
  }

  public RefreshResult refresh(String serializedRefreshToken) {
    return flowObservationFacade.observe("auth.refresh", observation -> {
      observation.retryPath("none").store(resolveStoreType());

      RefreshTokenPayload parsed;
      try {
        observation.stage("parse");
        parsed = refreshTokenParser.parse(serializedRefreshToken);
      } catch (RuntimeException ex) {
        observation.outcome("validation_failure").stage("parse");
        throw ex;
      }

      Instant now = Instant.now();

      RefreshTokenStore.RefreshTokenState knownState;
      try {
        observation.stage("validate_known_state");
        knownState = refreshTokenStore.createIfAbsent(initialStateFromParsedToken(parsed));
        refreshSessionValidatorChain.validateKnownState(knownState, now);
      } catch (RuntimeException ex) {
        classifyRefreshFailure(observation, ex, "validate_known_state");
        throw ex;
      }

      AuthAccessTokenIssuer.AccessTokenResult accessTokenResult;
      try {
        observation.stage("issue_access_token");
        accessTokenResult = accessTokenIssuer.issueAccessToken(knownState.meetingId(), knownState.subject());
      } catch (RuntimeException ex) {
        observation.outcome("partial_failure").stage("issue_access_token");
        throw ex;
      }

      RefreshRotationService.RotationResult rotationResult;
      try {
        observation.stage("rotate_refresh_token");
        rotationResult = refreshRotationService.rotate(knownState, now);
      } catch (RuntimeException ex) {
        classifyRefreshFailure(observation, ex, "rotate_refresh_token");
        throw ex;
      }

      RefreshTokenStore.ConsumeResult consumeResult;
      try {
        observation.stage("consume_previous_token");
        consumeResult = refreshTokenStore.consume(parsed.tokenId());
        refreshSessionValidatorChain.requireConsumable(consumeResult, parsed);
      } catch (RuntimeException ex) {
        classifyRefreshFailure(observation, ex, "consume_previous_token");
        throw ex;
      }

      try {
        observation.stage("persist_rotated_token");
        refreshTokenStore.create(rotationResult.nextState());
      } catch (RuntimeException ex) {
        classifyRefreshFailure(observation, ex, "persist_rotated_token");
        throw ex;
      }

      observation.outcome("success");
      return new RefreshResult(
          accessTokenResult.accessToken(),
          rotationResult.refreshToken(),
          accessTokenResult.expiresAt(),
          accessTokenResult.role(),
          "Bearer");
    });
  }

  private RefreshTokenStore.RefreshTokenState initialStateFromParsedToken(RefreshTokenPayload parsed) {
    Instant idleExpiresAt = minInstant(
        parsed.expiresAt(),
        parsed.issuedAt().plus(refreshProperties.idleTtlMinutes(), ChronoUnit.MINUTES));

    return new RefreshTokenStore.RefreshTokenState(
        parsed.tokenId(),
        parsed.subject(),
        parsed.meetingId(),
        parsed.expiresAt(),
        idleExpiresAt,
        RefreshTokenStore.TokenStatus.ACTIVE);
  }

  private Instant minInstant(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }

  private String resolveStoreType() {
    String simpleName = refreshTokenStore.getClass().getSimpleName().toLowerCase();
    if (simpleName.contains("redis")) {
      return "redis";
    }
    if (simpleName.contains("memory")) {
      return "in-memory";
    }
    return "custom";
  }

  private void classifyRefreshFailure(
      FlowObservationFacade.FlowObservation observation,
      RuntimeException ex,
      String stage) {
    observation.stage(stage);
    if (ex instanceof RetryableRefreshTokenException) {
      observation.outcome("contention").retryPath("redis_retry");
      return;
    }
    if (ex instanceof AuthTokenException authTokenException) {
      if (ErrorCode.CONFIG_INCOMPATIBLE.code().equals(authTokenException.errorCode())) {
        observation.outcome("policy_rejection").compatibility("incompatible");
        return;
      }
      observation.outcome("validation_failure");
      return;
    }
    observation.outcome("partial_failure");
  }
}
