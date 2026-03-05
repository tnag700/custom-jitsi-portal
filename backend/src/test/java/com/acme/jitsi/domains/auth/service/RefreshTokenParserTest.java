package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

class RefreshTokenParserTest {

  private static final String SECRET = "01234567890123456789012345678901";

  private JwtDecoder meetingJwtDecoder() {
    return meetingJwtDecoder(Clock.systemUTC());
  }

  private JwtDecoder meetingJwtDecoder(Clock clock) {
    SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build();
    JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
    timestampValidator.setClock(clock);
    decoder.setJwtValidator(timestampValidator);
    return decoder;
  }

  private JwtEncoder meetingJwtEncoder() {
    SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
  }

  @Test
  void parsesValidRefreshTokenClaims() throws Exception {
    Instant now = Instant.parse("2026-02-16T10:00:00Z");
    Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
    RefreshTokenParser parser = new RefreshTokenParser(
      meetingJwtDecoder(fixedClock),
      "https://portal.example.test",
      "jitsi-meet",
      fixedClock);
    String token = buildRefreshToken(
        "refresh-jti-1",
        "u-host",
        "meeting-a",
      now,
      now.plus(2, ChronoUnit.HOURS));

    RefreshTokenPayload payload = parser.parse(token);

    assertThat(payload.tokenId()).isEqualTo("refresh-jti-1");
    assertThat(payload.subject()).isEqualTo("u-host");
    assertThat(payload.meetingId()).isEqualTo("meeting-a");
  }

  @Test
  void rejectsMissingTokenWithAuthRequired() {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");

    assertThatThrownBy(() -> parser.parse(" "))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(exception.errorCode()).isEqualTo("AUTH_REQUIRED");
          assertThat(exception.getMessage()).isEqualTo("Сессия отсутствует. Выполните вход через SSO.");
        });
  }

  @Test
  void rejectsTokenWithWrongIssuer() throws Exception {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    String token = buildRefreshToken(
        "refresh-jti-issuer",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS),
        "https://other-issuer.example.test",
        "jitsi-meet",
        "refresh");

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
      .isEqualTo("TOKEN_INVALID");
  }

  @Test
  void rejectsTokenWithWrongType() throws Exception {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    String token = buildRefreshToken(
        "refresh-jti-type",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS),
        "https://portal.example.test",
        "jitsi-meet",
        "access");

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
      .isEqualTo("TOKEN_INVALID");
  }

  @Test
  void rejectsTokenWithWrongAudience() throws Exception {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    String token = buildRefreshToken(
        "refresh-jti-aud",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS),
        "https://portal.example.test",
        "other-audience",
        "refresh");

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
      .isEqualTo("TOKEN_INVALID");
  }

  @Test
  void rejectsTokenWithMissingMandatoryClaims() throws Exception {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    String token = buildRefreshTokenWithoutMeetingId(
        "refresh-jti-missing-claims",
        "u-host",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(exception.errorCode()).isEqualTo("TOKEN_INVALID");
          assertThat(exception.getMessage()).isEqualTo("Refresh-токен не содержит обязательные claims.");
        });
  }

  @Test
  void rejectsExpiredTokenWithAuthRequired() throws Exception {
    Instant now = Instant.parse("2026-02-16T10:00:00Z");
    Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
    RefreshTokenParser parser = new RefreshTokenParser(
      meetingJwtDecoder(fixedClock),
      "https://portal.example.test",
      "jitsi-meet",
      fixedClock);
    String token = buildRefreshToken(
        "refresh-jti-expired",
        "u-host",
        "meeting-a",
      now.minus(2, ChronoUnit.HOURS),
      now.minus(1, ChronoUnit.MINUTES));

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(exception.errorCode()).isEqualTo("AUTH_REQUIRED");
        });
  }

  @Test
  void rejectsTokenWithNonStringMeetingIdClaim() throws Exception {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    String token = buildRefreshTokenWithRawClaims(
        "refresh-jti-non-string-meeting-id",
        "u-host",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS),
        "https://portal.example.test",
        "jitsi-meet",
        "refresh",
        12345,
        "u-host");

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(exception.errorCode()).isEqualTo("TOKEN_INVALID");
          assertThat(exception.getMessage()).isEqualTo("Refresh-токен не содержит обязательные claims.");
        });
  }

  @Test
  void rejectsTokenWithNonStringTokenTypeClaim() throws Exception {
    RefreshTokenParser parser = new RefreshTokenParser(meetingJwtDecoder(), "https://portal.example.test", "jitsi-meet");
    String token = buildRefreshTokenWithRawClaims(
        "refresh-jti-non-string-token-type",
        "u-host",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS),
        "https://portal.example.test",
        "jitsi-meet",
        123,
        "meeting-a",
        "u-host");

    assertThatThrownBy(() -> parser.parse(token))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
          assertThat(exception.errorCode()).isEqualTo("TOKEN_INVALID");
          assertThat(exception.getMessage()).isEqualTo("Refresh-токен не содержит обязательные claims.");
        });
  }

  private String buildRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt) throws JOSEException {
    return buildRefreshToken(
        tokenId,
        subject,
        meetingId,
        issuedAt,
        expiresAt,
        "https://portal.example.test",
        "jitsi-meet",
        "refresh");
  }

  private String buildRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt,
      String issuer,
      String audience,
      String tokenType) throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .subject(subject)
        .issueTime(Date.from(issuedAt))
        .expirationTime(Date.from(expiresAt))
        .jwtID(tokenId)
        .claim("tokenType", tokenType)
        .claim("meetingId", meetingId)
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("JWT")).build(),
        claims);
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  private String buildRefreshTokenWithoutMeetingId(
      String tokenId,
      String subject,
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
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("JWT")).build(),
        claims);
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }

  private String buildRefreshTokenWithRawClaims(
      String tokenId,
      String subject,
      Instant issuedAt,
      Instant expiresAt,
      String issuer,
      String audience,
      Object tokenType,
      Object meetingId,
      Object subjectClaim) throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .audience(audience)
        .subject(subjectClaim == null ? null : subjectClaim.toString())
        .issueTime(Date.from(issuedAt))
        .expirationTime(Date.from(expiresAt))
        .jwtID(tokenId)
        .claim("tokenType", tokenType)
        .claim("meetingId", meetingId)
        .build();

    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.HS256).type(new JOSEObjectType("JWT")).build(),
        claims);
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}