package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenFlowCompatibilityGuard;
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
        org.mockito.Mockito.mock(TokenFlowCompatibilityGuard.class),
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
        org.mockito.Mockito.mock(TokenFlowCompatibilityGuard.class),
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
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("CONFIG_INCOMPATIBLE");
  }
}