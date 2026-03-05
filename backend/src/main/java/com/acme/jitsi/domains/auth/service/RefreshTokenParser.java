package com.acme.jitsi.domains.auth.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenParser {

  private final JwtDecoder jwtDecoder;
  private final String issuer;
  private final String audience;
  private final Clock clock;

  @Autowired
  RefreshTokenParser(
      JwtDecoder jwtDecoder,
      @Value("${app.meetings.token.issuer:jitsi-portal}") String issuer,
      @Value("${app.meetings.token.audience:jitsi-meet}") String audience) {
    this(jwtDecoder, issuer, audience, Clock.systemUTC());
  }

  RefreshTokenParser(
      JwtDecoder jwtDecoder,
      String issuer,
      String audience,
      Clock clock) {
    this.jwtDecoder = jwtDecoder;
    this.issuer = issuer;
    this.audience = audience;
    this.clock = clock;
  }

  RefreshTokenPayload parse(String serializedRefreshToken) {
    if (serializedRefreshToken == null || serializedRefreshToken.isBlank()) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Сессия отсутствует. Выполните вход через SSO.");
    }

    Jwt jwt = decodeJwt(serializedRefreshToken);
    validateIssuerAndAudience(jwt);
    String meetingId = validateTokenTypeAndMeetingId(jwt);
    return toPayload(jwt, meetingId);
  }

  private String requiredStringClaim(Jwt jwt, String claimName) {
    Object rawValue = jwt.getClaim(claimName);
    if (!(rawValue instanceof String stringValue) || stringValue.isBlank()) {
      return null;
    }
    return stringValue;
  }

  private boolean isExpiredJwt(JwtException ex) {
    String message = ex.getMessage();
    if (message == null) {
      return false;
    }

    String normalized = message.toLowerCase(Locale.ROOT);
    return normalized.contains("expired") || normalized.contains("expires") || normalized.contains("exp");
  }

  private Jwt decodeJwt(String serializedRefreshToken) {
    try {
      return jwtDecoder.decode(serializedRefreshToken);
    } catch (JwtException ex) {
      if (isExpiredJwt(ex)) {
        throw new MeetingTokenException(
            HttpStatus.UNAUTHORIZED,
            "AUTH_REQUIRED",
            "Сессия истекла. Выполните вход через SSO.",
            ex);
      }
      throw new MeetingTokenException(
          HttpStatus.UNAUTHORIZED,
          "TOKEN_INVALID",
          "Некорректный refresh-токен.",
          ex);
    }
  }

  private void validateIssuerAndAudience(Jwt jwt) {
    if (!Objects.equals(issuer, jwt.getClaimAsString("iss"))) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Issuer refresh-токена не поддерживается.");
    }
    if (jwt.getAudience() == null || !jwt.getAudience().contains(audience)) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Audience refresh-токена не поддерживается.");
    }
  }

  private String validateTokenTypeAndMeetingId(Jwt jwt) {
    String tokenType = requiredStringClaim(jwt, "tokenType");
    String meetingId = requiredStringClaim(jwt, "meetingId");
    if (tokenType == null || meetingId == null) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Refresh-токен не содержит обязательные claims.");
    }
    if (!"refresh".equals(tokenType.toLowerCase(Locale.ROOT))) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Требуется refresh-токен.");
    }
    return meetingId;
  }

  private RefreshTokenPayload toPayload(Jwt jwt, String meetingId) {
    String tokenId = jwt.getId();
    String subject = jwt.getSubject();
    Instant issuedAt = jwt.getIssuedAt();
    Instant expiresAt = jwt.getExpiresAt();

    if (isBlank(tokenId) || isBlank(subject) || isBlank(meetingId) || issuedAt == null || expiresAt == null) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "Refresh-токен не содержит обязательные claims.");
    }
    if (clock.instant().isAfter(expiresAt)) {
      throw new MeetingTokenException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Сессия истекла. Выполните вход через SSO.");
    }
    return new RefreshTokenPayload(tokenId, subject, meetingId, issuedAt, expiresAt);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}