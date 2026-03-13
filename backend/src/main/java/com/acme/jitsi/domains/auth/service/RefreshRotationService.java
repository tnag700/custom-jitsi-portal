package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
class RefreshRotationService {

  record RotationResult(String refreshToken, RefreshTokenStore.RefreshTokenState nextState) {
  }

  private final AuthRefreshProperties refreshProperties;
  private final JwtEncoder jwtEncoder;
  private final JwtAlgorithmPolicy algorithmPolicy;
  private final TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy;
  private final String issuer;
  private final String audience;
  private final String algorithm;
    private final FlowObservationFacade flowObservationFacade;

    RefreshRotationService(
      AuthRefreshProperties refreshProperties,
      JwtEncoder jwtEncoder,
      JwtAlgorithmPolicy algorithmPolicy,
      TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy,
      @Value("${app.meetings.token.issuer:jitsi-portal}") String issuer,
      @Value("${app.meetings.token.audience:jitsi-meet}") String audience,
      @Value("${app.meetings.token.algorithm:HS256}") String algorithm) {
    this(
      refreshProperties,
      jwtEncoder,
      algorithmPolicy,
      tokenIssuanceCompatibilityPolicy,
      FlowObservationFacade.noop(),
      issuer,
      audience,
      algorithm);
    }

  @Autowired
  RefreshRotationService(
      AuthRefreshProperties refreshProperties,
      JwtEncoder jwtEncoder,
      JwtAlgorithmPolicy algorithmPolicy,
      TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy,
      FlowObservationFacade flowObservationFacade,
      @Value("${app.meetings.token.issuer:jitsi-portal}") String issuer,
      @Value("${app.meetings.token.audience:jitsi-meet}") String audience,
      @Value("${app.meetings.token.algorithm:HS256}") String algorithm) {
    this.refreshProperties = refreshProperties;
    this.jwtEncoder = jwtEncoder;
    this.algorithmPolicy = algorithmPolicy;
    this.tokenIssuanceCompatibilityPolicy = tokenIssuanceCompatibilityPolicy;
    this.flowObservationFacade = flowObservationFacade;
    this.issuer = issuer;
    this.audience = audience;
    this.algorithm = algorithm;
  }

  RotationResult rotate(RefreshTokenStore.RefreshTokenState consumedState, Instant now) {
    return flowObservationFacade.observe("auth.refresh.rotation", observation -> {
      try {
        observation.stage("compatibility_lookup");
        tokenIssuanceCompatibilityPolicy.assertTokenIssuanceAllowed();
        observation.compatibility("compatible");
      } catch (TokenIssuancePolicyException ex) {
        observation.outcome("policy_rejection").stage("compatibility_lookup").compatibility("incompatible");
        throw new AuthTokenException(ex.status(), ex.errorCode(), ex.getMessage(), ex);
      }
      String nextTokenId = UUID.randomUUID().toString();
      Instant nextIdleExpiresAt = minInstant(
          consumedState.absoluteExpiresAt(),
          now.plus(refreshProperties.idleTtlMinutes(), ChronoUnit.MINUTES));

      try {
        observation.stage("issue_token");
        String nextRefreshToken = issueRefreshToken(
            nextTokenId,
            consumedState.subject(),
            consumedState.meetingId(),
            now,
            consumedState.absoluteExpiresAt());

        RefreshTokenStore.RefreshTokenState nextState = new RefreshTokenStore.RefreshTokenState(
            nextTokenId,
            consumedState.subject(),
            consumedState.meetingId(),
            consumedState.absoluteExpiresAt(),
            nextIdleExpiresAt,
            RefreshTokenStore.TokenStatus.ACTIVE);
        observation.outcome("success");
        return new RotationResult(nextRefreshToken, nextState);
      } catch (RuntimeException ex) {
        observation.outcome("partial_failure").stage("issue_token");
        throw ex;
      }
    });
  }

  private String issueRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer(issuer)
        .audience(List.of(audience))
        .subject(subject)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .id(tokenId)
        .claim("tokenType", "refresh")
        .claim("meetingId", meetingId)
        .build();

    JwsHeader headers = JwsHeader.with(resolveMacAlgorithm()).build();

    try {
      return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    } catch (JwtException ex) {
      throw new AuthTokenException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.INTERNAL_ERROR.code(),
          "Не удалось выпустить refresh-токен.",
          ex);
    }
  }

  private MacAlgorithm resolveMacAlgorithm() {
    try {
      return algorithmPolicy.resolveMacAlgorithmForSecret(algorithm);
    } catch (IllegalArgumentException ex) {
      throw new AuthTokenException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          ErrorCode.INTERNAL_ERROR.code(),
          "Неподдерживаемый алгоритм подписи refresh-токена.",
          ex);
    }
  }

  private Instant minInstant(Instant first, Instant second) {
    return first.isBefore(second) ? first : second;
  }
}