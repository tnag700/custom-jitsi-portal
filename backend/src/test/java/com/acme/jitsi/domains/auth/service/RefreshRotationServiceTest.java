package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class RefreshRotationServiceTest {

  private static final String SECRET = "01234567890123456789012345678901";

  private JwtEncoder meetingJwtEncoder() {
    SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
  }

  @Test
  void rotatesRefreshTokenAndClampsIdleExpirationToAbsolute() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(120);

    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        new DefaultJwtAlgorithmPolicy(),
        org.mockito.Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    Instant now = Instant.now();
    Instant absoluteExpiresAt = now.plus(30, ChronoUnit.MINUTES);
    RefreshTokenStore.RefreshTokenState consumedState = new RefreshTokenStore.RefreshTokenState(
        "consumed-jti-1",
        "u-host",
        "meeting-a",
        absoluteExpiresAt,
        now.plus(10, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.USED);

    RefreshRotationService.RotationResult result = rotationService.rotate(consumedState, now);

    assertThat(result.refreshToken()).isNotBlank();
    assertThat(result.nextState().tokenId()).isNotEqualTo("consumed-jti-1");
    assertThat(result.nextState().subject()).isEqualTo("u-host");
    assertThat(result.nextState().meetingId()).isEqualTo("meeting-a");
    assertThat(result.nextState().absoluteExpiresAt()).isEqualTo(absoluteExpiresAt);
    assertThat(result.nextState().idleExpiresAt()).isEqualTo(absoluteExpiresAt);
    assertThat(result.nextState().status()).isEqualTo(RefreshTokenStore.TokenStatus.ACTIVE);
  }

  @Test
  void keepsConfigIncompatibleErrorForUnsupportedAlgorithm() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);

    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        new DefaultJwtAlgorithmPolicy(),
        org.mockito.Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
        "https://portal.example.test",
        "jitsi-meet",
        "RS256");

    Instant now = Instant.now();
    RefreshTokenStore.RefreshTokenState consumedState = new RefreshTokenStore.RefreshTokenState(
        "consumed-jti-2",
        "u-host",
        "meeting-a",
        now.plus(2, ChronoUnit.HOURS),
        now.plus(30, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.USED);

    assertThatThrownBy(() -> rotationService.rotate(consumedState, now))
      .isInstanceOf(AuthTokenException.class)
      .extracting(error -> ((AuthTokenException) error).errorCode())
        .isEqualTo(ErrorCode.INTERNAL_ERROR.code());
  }

  @Test
  void rotatePreservesConfigIncompatibleContractWhenCompatibilityBlocksIssuance() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    TokenIssuanceCompatibilityPolicy compatibilityPolicy = org.mockito.Mockito.mock(TokenIssuanceCompatibilityPolicy.class);
    doThrow(new TokenIssuancePolicyException(
        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CONFIG_INCOMPATIBLE.code(),
      "Token issuance is blocked due to incompatible active config set: cs-1"))
        .when(compatibilityPolicy)
        .assertTokenIssuanceAllowed();

    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
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
        .isInstanceOf(AuthTokenException.class)
        .satisfies(error -> {
          AuthTokenException exception = (AuthTokenException) error;
          assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFIG_INCOMPATIBLE.code());
          assertThat(exception.getMessage()).contains("Token issuance is blocked");
        });
  }
}