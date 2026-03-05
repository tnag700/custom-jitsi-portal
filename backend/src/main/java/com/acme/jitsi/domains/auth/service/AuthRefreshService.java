package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthRefreshService {

  public record RefreshResult(String accessToken, String refreshToken, Instant expiresAt, String role, String tokenType) {
  }

  private final MeetingTokenIssuer meetingAccessTokenService;
  private final RefreshTokenStore refreshTokenStore;
  private final AuthRefreshProperties refreshProperties;
  private final RefreshTokenParser refreshTokenParser;
  private final RefreshSessionValidatorChain refreshSessionValidatorChain;
  private final RefreshRotationService refreshRotationService;
  private final RefreshSecurityEventPublisher securityEventPublisher;

  AuthRefreshService(
      MeetingTokenIssuer meetingAccessTokenService,
      RefreshTokenStore refreshTokenStore,
      AuthRefreshProperties refreshProperties,
      RefreshTokenParser refreshTokenParser,
      RefreshSessionValidatorChain refreshSessionValidatorChain,
      RefreshRotationService refreshRotationService,
      RefreshSecurityEventPublisher securityEventPublisher) {
    this.meetingAccessTokenService = meetingAccessTokenService;
    this.refreshTokenStore = refreshTokenStore;
    this.refreshProperties = refreshProperties;
    this.refreshTokenParser = refreshTokenParser;
    this.refreshSessionValidatorChain = refreshSessionValidatorChain;
    this.refreshRotationService = refreshRotationService;
    this.securityEventPublisher = securityEventPublisher;

    if (refreshProperties.revokedTokenIds() != null) {
      refreshProperties.revokedTokenIds().stream()
          .filter(tokenId -> tokenId != null && !tokenId.isBlank())
          .forEach(refreshTokenStore::revoke);
    }
  }

  public void revoke(String tokenId) {
    if (tokenId == null || tokenId.isBlank()) {
      throw new MeetingTokenException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Требуется идентификатор refresh-токена.");
    }

    refreshTokenStore.revoke(tokenId);
    securityEventPublisher.publish("REFRESH_REVOKED", "TOKEN_REVOKED", tokenId, "", "");
  }

  public RefreshResult refresh(String serializedRefreshToken) {
    RefreshTokenPayload parsed = refreshTokenParser.parse(serializedRefreshToken);
    Instant now = Instant.now();

    RefreshTokenStore.RefreshTokenState knownState = refreshTokenStore.createIfAbsent(initialStateFromParsedToken(parsed));
    refreshSessionValidatorChain.validateKnownState(knownState, now);

    RefreshTokenStore.ConsumeResult consumeResult = refreshTokenStore.consume(parsed.tokenId());
    RefreshTokenStore.RefreshTokenState consumedState = refreshSessionValidatorChain.requireConsumable(consumeResult, parsed);

    MeetingTokenIssuer.AccessTokenResult accessTokenResult =
        meetingAccessTokenService.issueAccessToken(consumedState.meetingId(), consumedState.subject());

    RefreshRotationService.RotationResult rotationResult = refreshRotationService.rotate(consumedState, now);
    refreshTokenStore.create(rotationResult.nextState());

    return new RefreshResult(
        accessTokenResult.accessToken(),
        rotationResult.refreshToken(),
        accessTokenResult.expiresAt(),
        accessTokenResult.role(),
        "Bearer");
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
}
