package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.argThat;

import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenFlowCompatibilityGuard;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthRefreshServiceTest {

    private static final String SECRET = "01234567890123456789012345678901";
    private static final DefaultJwtAlgorithmPolicy DEFAULT_JWT_ALGORITHM_POLICY = new DefaultJwtAlgorithmPolicy();

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

  @Test
  void refreshReturnsNewTokensAndStoresRotatedStateOnHappyPath() throws Exception {
    MeetingTokenIssuer accessTokenService = mock(MeetingTokenIssuer.class);
    RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    RefreshSecurityEventPublisher securityEventPublisher = new RefreshSecurityEventPublisher(eventPublisher, java.time.Clock.systemUTC());
    RefreshTokenParser refreshTokenParser = new RefreshTokenParser(
        meetingJwtDecoder(),
        "https://portal.example.test",
        "jitsi-meet");
    RefreshSessionValidatorChain validatorChain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        DEFAULT_JWT_ALGORITHM_POLICY,
        mock(TokenFlowCompatibilityGuard.class),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    AuthRefreshService service = new AuthRefreshService(
        accessTokenService,
        refreshTokenStore,
        properties,
        refreshTokenParser,
        validatorChain,
        rotationService,
        securityEventPublisher);

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
    when(accessTokenService.issueAccessToken("meeting-a", "u-host"))
        .thenReturn(new MeetingTokenIssuer.AccessTokenResult("access-token", issuedAt.plus(20, ChronoUnit.MINUTES), "host"));

    AuthRefreshService.RefreshResult result = service.refresh(token);

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isNotBlank();
    assertThat(result.role()).isEqualTo("host");
    assertThat(result.tokenType()).isEqualTo("Bearer");
    verify(refreshTokenStore).create(argThat(state ->
        state.subject().equals("u-host")
            && state.meetingId().equals("meeting-a")
            && state.status() == RefreshTokenStore.TokenStatus.ACTIVE));
  }

  @Test
  void publishesReuseSecurityEventWhenConsumedTokenIsAlreadyUsed() throws Exception {
    MeetingTokenIssuer accessTokenService = mock(MeetingTokenIssuer.class);
    RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    RefreshSecurityEventPublisher securityEventPublisher = new RefreshSecurityEventPublisher(eventPublisher, java.time.Clock.systemUTC());
    RefreshTokenParser refreshTokenParser = new RefreshTokenParser(
        meetingJwtDecoder(),
        "https://portal.example.test",
        "jitsi-meet");
    RefreshSessionValidatorChain validatorChain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        DEFAULT_JWT_ALGORITHM_POLICY,
        mock(TokenFlowCompatibilityGuard.class),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    AuthRefreshService service = new AuthRefreshService(
        accessTokenService,
        refreshTokenStore,
        properties,
        refreshTokenParser,
        validatorChain,
        rotationService,
        securityEventPublisher);

    String token = buildRefreshToken(
        "reuse-jti-1",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    RefreshTokenStore.RefreshTokenState activeState = new RefreshTokenStore.RefreshTokenState(
        "reuse-jti-1",
        "u-host",
        "meeting-a",
        Instant.now().plus(2, ChronoUnit.HOURS),
        Instant.now().plus(1, ChronoUnit.HOURS),
        RefreshTokenStore.TokenStatus.ACTIVE);

    when(refreshTokenStore.createIfAbsent(any())).thenReturn(activeState);
    when(refreshTokenStore.consume("reuse-jti-1"))
        .thenReturn(new RefreshTokenStore.ConsumeResult(RefreshTokenStore.ConsumeStatus.USED, activeState));

    assertThatThrownBy(() -> service.refresh(token))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("REFRESH_REUSE_DETECTED");

    verify(eventPublisher).publishEvent(any(AuthRefreshSecurityEvent.class));
    verify(accessTokenService, never()).issueAccessToken(any(), any());
  }

  @Test
  void publishesRevokedSecurityEventWhenTokenIdIsPreRevoked() throws Exception {
    MeetingTokenIssuer accessTokenService = mock(MeetingTokenIssuer.class);
    RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    properties.setRevokedTokenIds(Set.of("revoked-jti-1"));
    RefreshSecurityEventPublisher securityEventPublisher = new RefreshSecurityEventPublisher(eventPublisher, java.time.Clock.systemUTC());
    RefreshTokenParser refreshTokenParser = new RefreshTokenParser(
        meetingJwtDecoder(),
        "https://portal.example.test",
        "jitsi-meet");
    RefreshSessionValidatorChain validatorChain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        DEFAULT_JWT_ALGORITHM_POLICY,
        mock(TokenFlowCompatibilityGuard.class),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    AuthRefreshService service = new AuthRefreshService(
        accessTokenService,
        refreshTokenStore,
        properties,
        refreshTokenParser,
        validatorChain,
        rotationService,
        securityEventPublisher);

    String token = buildRefreshToken(
        "revoked-jti-1",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    RefreshTokenStore.RefreshTokenState revokedState = new RefreshTokenStore.RefreshTokenState(
        "revoked-jti-1",
        "u-host",
        "meeting-a",
        Instant.now().plus(2, ChronoUnit.HOURS),
        Instant.now().plus(1, ChronoUnit.HOURS),
        RefreshTokenStore.TokenStatus.REVOKED);
    when(refreshTokenStore.createIfAbsent(any())).thenReturn(revokedState);

    assertThatThrownBy(() -> service.refresh(token))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).status())
        .isEqualTo(HttpStatus.FORBIDDEN);

    verify(refreshTokenStore, atLeastOnce()).revoke("revoked-jti-1");
    verify(eventPublisher).publishEvent(any(AuthRefreshSecurityEvent.class));
    verify(accessTokenService, never()).issueAccessToken(any(), any());
  }

  @Test
  void publishesExpiredSecurityEventWhenIdleOrAbsoluteTtlExpired() throws Exception {
    MeetingTokenIssuer accessTokenService = mock(MeetingTokenIssuer.class);
    RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    RefreshSecurityEventPublisher securityEventPublisher = new RefreshSecurityEventPublisher(eventPublisher, java.time.Clock.systemUTC());
    RefreshTokenParser refreshTokenParser = new RefreshTokenParser(
        meetingJwtDecoder(),
        "https://portal.example.test",
        "jitsi-meet");
    RefreshSessionValidatorChain validatorChain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        DEFAULT_JWT_ALGORITHM_POLICY,
        mock(TokenFlowCompatibilityGuard.class),
        "https://portal.example.test",
        "jitsi-meet",
        "HS256");

    AuthRefreshService service = new AuthRefreshService(
        accessTokenService,
        refreshTokenStore,
        properties,
        refreshTokenParser,
        validatorChain,
        rotationService,
        securityEventPublisher);

    String token = buildRefreshToken(
        "expired-jti-1",
        "u-host",
        "meeting-a",
        Instant.now().minus(3, ChronoUnit.HOURS),
        Instant.now().plus(1, ChronoUnit.HOURS));

    RefreshTokenStore.RefreshTokenState expiredState = new RefreshTokenStore.RefreshTokenState(
        "expired-jti-1",
        "u-host",
        "meeting-a",
        Instant.now().plus(1, ChronoUnit.HOURS),
        Instant.now().minus(1, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.ACTIVE);

    when(refreshTokenStore.createIfAbsent(any())).thenReturn(expiredState);

    assertThatThrownBy(() -> service.refresh(token))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("AUTH_REQUIRED");

    verify(eventPublisher).publishEvent(any(AuthRefreshSecurityEvent.class));
    verify(refreshTokenStore, never()).consume(any());
    verify(accessTokenService, never()).issueAccessToken(any(), any());
  }

  @Test
  void keepsUnsupportedAlgorithmErrorCodeForRefreshIssuance() throws Exception {
    MeetingTokenIssuer accessTokenService = mock(MeetingTokenIssuer.class);
    RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setIdleTtlMinutes(60);
    RefreshSecurityEventPublisher securityEventPublisher = new RefreshSecurityEventPublisher(eventPublisher, java.time.Clock.systemUTC());
    RefreshTokenParser refreshTokenParser = new RefreshTokenParser(
        meetingJwtDecoder(),
        "https://portal.example.test",
        "jitsi-meet");
    RefreshSessionValidatorChain validatorChain = new RefreshSessionValidatorChain(securityEventPublisher);
    RefreshRotationService rotationService = new RefreshRotationService(
        properties,
        meetingJwtEncoder(),
        DEFAULT_JWT_ALGORITHM_POLICY,
        mock(TokenFlowCompatibilityGuard.class),
        "https://portal.example.test",
        "jitsi-meet",
        "RS256");

    AuthRefreshService service = new AuthRefreshService(
        accessTokenService,
        refreshTokenStore,
        properties,
        refreshTokenParser,
        validatorChain,
        rotationService,
        securityEventPublisher);

    Instant issuedAt = Instant.now();
    Instant expiresAt = issuedAt.plus(2, ChronoUnit.HOURS);
    String token = buildRefreshToken("refresh-unsupported-alg", "u-host", "meeting-a", issuedAt, expiresAt);

    RefreshTokenStore.RefreshTokenState activeState = new RefreshTokenStore.RefreshTokenState(
        "refresh-unsupported-alg",
        "u-host",
        "meeting-a",
        expiresAt,
        issuedAt.plus(30, ChronoUnit.MINUTES),
        RefreshTokenStore.TokenStatus.ACTIVE);

    when(refreshTokenStore.createIfAbsent(any())).thenReturn(activeState);
    when(refreshTokenStore.consume("refresh-unsupported-alg"))
        .thenReturn(new RefreshTokenStore.ConsumeResult(RefreshTokenStore.ConsumeStatus.CONSUMED, activeState));
    when(accessTokenService.issueAccessToken("meeting-a", "u-host"))
        .thenReturn(new MeetingTokenIssuer.AccessTokenResult("access-token", issuedAt.plus(20, ChronoUnit.MINUTES), "host"));

    assertThatThrownBy(() -> service.refresh(token))
        .isInstanceOf(MeetingTokenException.class)
                .satisfies(error -> {
                    MeetingTokenException exception = (MeetingTokenException) error;
                    assertThat(exception.errorCode()).isEqualTo("CONFIG_INCOMPATIBLE");
                    assertThat(exception.getMessage()).isEqualTo("Неподдерживаемый алгоритм подписи refresh-токена.");
                });
  }

  private String buildRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt) throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("https://portal.example.test")
        .audience("jitsi-meet")
        .subject(subject)
        .issueTime(Date.from(issuedAt))
        .expirationTime(Date.from(expiresAt))
        .jwtID(tokenId)
        .claim("tokenType", "refresh")
        .claim("meetingId", meetingId)
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("JWT")).build(),
        claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}
