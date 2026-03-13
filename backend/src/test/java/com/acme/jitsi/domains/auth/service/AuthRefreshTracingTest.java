package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import com.acme.jitsi.shared.observability.RecordedObservationHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthRefreshTracingTest {

  private static final String SECRET = "01234567890123456789012345678901";

  private final RecordedObservationHandler observations = new RecordedObservationHandler();

  private AuthAccessTokenIssuer accessTokenIssuer;
  private RefreshTokenStore refreshTokenStore;
  private TokenIssuanceCompatibilityPolicy compatibilityPolicy;
  private AuthRefreshService service;

  @BeforeEach
  void setUp() {
    accessTokenIssuer = mock(AuthAccessTokenIssuer.class);
    refreshTokenStore = mock(RefreshTokenStore.class);
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    compatibilityPolicy = mock(TokenIssuanceCompatibilityPolicy.class);
    RefreshSecurityEventPublisher securityEventPublisher =
        new RefreshSecurityEventPublisher(mock(ApplicationEventPublisher.class), Clock.systemUTC());
    RefreshTokenParser refreshTokenParser =
        new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    RefreshSessionValidatorChain validatorChain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    service = new AuthRefreshService(
        accessTokenIssuer,
        refreshTokenStore,
        properties,
        refreshTokenParser,
        validatorChain,
        rotationService,
        securityEventPublisher,
        new FlowObservationFacade(observations.createRegistry()));
    observations.reset();
  }

  @Test
  void refreshEmitsCanonicalSuccessObservationWithoutTokenLeakage() throws Exception {
    Instant issuedAt = Instant.now();
    Instant absoluteExpiresAt = issuedAt.plus(2, ChronoUnit.HOURS);
    String token = buildRefreshToken("happy-jti-1", "u-host", "meeting-a", issuedAt, absoluteExpiresAt);
    RefreshTokenStore.RefreshTokenState activeState = new RefreshTokenStore.RefreshTokenState(
        "happy-jti-1",
        "u-host",
        "meeting-a",
        absoluteExpiresAt,
        issuedAt.plus(30, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.ACTIVE);

    when(refreshTokenStore.createIfAbsent(any())).thenReturn(activeState);
    when(refreshTokenStore.consume("happy-jti-1"))
        .thenReturn(new RefreshTokenStore.ConsumeResult(RefreshTokenStore.ConsumeStatus.CONSUMED, activeState));
    when(accessTokenIssuer.issueAccessToken("meeting-a", "u-host"))
        .thenReturn(new AuthAccessTokenIssuer.AccessTokenResult("access-token", issuedAt.plus(20, ChronoUnit.MINUTES), "host"));

    service.refresh(token);

    RecordedObservationHandler.RecordedObservation observation = observations.only("auth.refresh");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "success")
        .containsEntry("flow.stage", "persist_rotated_token")
        .containsEntry("flow.retry_path", "none")
        .containsEntry("flow.store", "custom");
    assertThat(observation.lowCardinality().toString())
        .doesNotContain(token)
        .doesNotContain("happy-jti-1")
        .doesNotContain("u-host")
        .doesNotContain("access-token");
    verify(refreshTokenStore).create(argThat(state -> state.meetingId().equals("meeting-a")));
  }

  @Test
  void refreshMarksValidationFailureWhenParsingFails() {
    assertThatThrownBy(() -> service.refresh("not-a-jwt"))
        .isInstanceOf(AuthTokenException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("auth.refresh");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "validation_failure")
        .containsEntry("flow.stage", "parse");
  }

    @Test
    void refreshMarksPolicyRejectionOnRotateStageWhenCompatibilityBlocksRotation() throws Exception {
    Instant issuedAt = Instant.now();
    Instant absoluteExpiresAt = issuedAt.plus(2, ChronoUnit.HOURS);
    String token = buildRefreshToken("policy-jti-1", "u-host", "meeting-a", issuedAt, absoluteExpiresAt);
    RefreshTokenStore.RefreshTokenState activeState = new RefreshTokenStore.RefreshTokenState(
      "policy-jti-1",
      "u-host",
      "meeting-a",
      absoluteExpiresAt,
      issuedAt.plus(30, ChronoUnit.MINUTES),
      RefreshTokenStore.TokenStatus.ACTIVE);

    when(refreshTokenStore.createIfAbsent(any())).thenReturn(activeState);
    when(accessTokenIssuer.issueAccessToken("meeting-a", "u-host"))
      .thenReturn(new AuthAccessTokenIssuer.AccessTokenResult("access-token", issuedAt.plus(20, ChronoUnit.MINUTES), "host"));
    org.mockito.Mockito.doThrow(new com.acme.jitsi.security.TokenIssuancePolicyException(
      org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
      ErrorCode.CONFIG_INCOMPATIBLE.code(),
      "Token issuance is blocked due to incompatible active config set: cs-1"))
      .when(compatibilityPolicy)
      .assertTokenIssuanceAllowed();

    assertThatThrownBy(() -> service.refresh(token))
      .isInstanceOf(AuthTokenException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("auth.refresh");
    assertThat(observation.lowCardinality())
      .containsEntry("flow.outcome", "policy_rejection")
      .containsEntry("flow.stage", "rotate_refresh_token")
      .containsEntry("flow.compatibility", "incompatible");
    }

    @Test
    void refreshMarksContentionWhenConsumePathHitsRetryableStoreFailure() throws Exception {
        Instant issuedAt = Instant.now();
        Instant absoluteExpiresAt = issuedAt.plus(2, ChronoUnit.HOURS);
        String token = buildRefreshToken("retry-jti-1", "u-host", "meeting-a", issuedAt, absoluteExpiresAt);
        RefreshTokenStore.RefreshTokenState activeState = new RefreshTokenStore.RefreshTokenState(
                "retry-jti-1",
                "u-host",
                "meeting-a",
                absoluteExpiresAt,
                issuedAt.plus(30, ChronoUnit.MINUTES),
                RefreshTokenStore.TokenStatus.ACTIVE);

        when(refreshTokenStore.createIfAbsent(any())).thenReturn(activeState);
        when(accessTokenIssuer.issueAccessToken("meeting-a", "u-host"))
                .thenReturn(new AuthAccessTokenIssuer.AccessTokenResult("access-token", issuedAt.plus(20, ChronoUnit.MINUTES), "host"));
        when(refreshTokenStore.consume("retry-jti-1"))
                .thenThrow(new RetryableRefreshTokenException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        ErrorCode.CONFIG_INCOMPATIBLE.code(),
                        "Redis недоступен для атомарного учета токенов."));

        assertThatThrownBy(() -> service.refresh(token))
                .isInstanceOf(RetryableRefreshTokenException.class);

        RecordedObservationHandler.RecordedObservation observation = observations.only("auth.refresh");
        assertThat(observation.lowCardinality())
                .containsEntry("flow.outcome", "contention")
                .containsEntry("flow.stage", "consume_previous_token")
                .containsEntry("flow.retry_path", "redis_retry");
    }

  private JwtEncoder meetingJwtEncoder() {
    SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
  }

  private JwtDecoder meetingJwtDecoder() {
    SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
  }

  private String buildRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt) throws Exception {
    com.nimbusds.jwt.SignedJWT jwt = new com.nimbusds.jwt.SignedJWT(
        new com.nimbusds.jose.JWSHeader.Builder(com.nimbusds.jose.JWSAlgorithm.HS256)
            .type(com.nimbusds.jose.JOSEObjectType.JWT)
            .build(),
        new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer("https://portal.example.test")
            .audience("jitsi-meet")
            .jwtID(tokenId)
            .subject(subject)
            .issueTime(java.util.Date.from(issuedAt))
            .expirationTime(java.util.Date.from(expiresAt))
            .claim("tokenType", "refresh")
            .claim("meetingId", meetingId)
            .build());
    jwt.sign(new com.nimbusds.jose.crypto.MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}