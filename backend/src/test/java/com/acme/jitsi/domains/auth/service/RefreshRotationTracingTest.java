package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import com.acme.jitsi.shared.observability.RecordedObservationHandler;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class RefreshRotationTracingTest {

  private static final String SECRET = "01234567890123456789012345678901";

  private final RecordedObservationHandler observations = new RecordedObservationHandler();

  private AuthRefreshProperties properties;
  private TokenIssuanceCompatibilityPolicy compatibilityPolicy;

  @BeforeEach
  void setUp() {
    properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    compatibilityPolicy = mock(TokenIssuanceCompatibilityPolicy.class);
    observations.reset();
  }

  @Test
  void rotateEmitsPolicyRejectionObservation() {
    doThrow(new TokenIssuancePolicyException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CONFIG_INCOMPATIBLE.code(),
        "Token issuance is blocked due to incompatible active config set: cs-1"))
        .when(compatibilityPolicy)
        .assertTokenIssuanceAllowed();

    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    Instant now = Instant.now();
    RefreshTokenStore.RefreshTokenState consumedState = new RefreshTokenStore.RefreshTokenState(
        "consumed-jti-3",
        "u-host",
        "meeting-a",
        now.plus(2, ChronoUnit.HOURS),
        now.plus(30, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.USED);

    assertThatThrownBy(() -> rotationService.rotate(consumedState, now))
        .isInstanceOf(AuthTokenException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("auth.refresh.rotation");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "policy_rejection")
        .containsEntry("flow.stage", "compatibility_lookup")
        .containsEntry("flow.compatibility", "incompatible");
  }

  @Test
  void rotateEmitsSuccessObservationWithCompatibilityCompatible() {
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    Instant now = Instant.now();
    RefreshTokenStore.RefreshTokenState consumedState = new RefreshTokenStore.RefreshTokenState(
        "consumed-jti-success",
        "u-host",
        "meeting-a",
        now.plus(2, java.time.temporal.ChronoUnit.HOURS),
        now.plus(30, java.time.temporal.ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.USED);

    RefreshRotationService.RotationResult result = rotationService.rotate(consumedState, now);

    assertThat(result.refreshToken()).isNotBlank();
    RecordedObservationHandler.RecordedObservation observation = observations.only("auth.refresh.rotation");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "success")
        .containsEntry("flow.stage", "issue_token")
        .containsEntry("flow.compatibility", "compatible");
    assertThat(observation.lowCardinality().toString())
        .doesNotContain("consumed-jti-success")
        .doesNotContain("u-host");
  }

  private JwtEncoder meetingJwtEncoder() {
    SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
  }
}