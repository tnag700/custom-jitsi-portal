package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.security.JwtAlgorithmPolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.nimbusds.jose.JOSEObjectType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

final class MeetingAccessTokenClaimsFactory {

  private final MeetingTokenProperties properties;
  private final JwtEncoder jwtEncoder;
  private final JwtAlgorithmPolicy algorithmPolicy;
  private final Clock clock;

  MeetingAccessTokenClaimsFactory(
      MeetingTokenProperties properties,
      JwtEncoder jwtEncoder,
      JwtAlgorithmPolicy algorithmPolicy,
      Clock clock) {
    this.properties = properties;
    this.jwtEncoder = jwtEncoder;
    this.algorithmPolicy = algorithmPolicy;
    this.clock = clock;
  }

  MeetingTokenIssuer.AccessTokenResult issue(
      String meetingId,
      String room,
      String subject,
      MeetingRole role,
      boolean guest,
      String displayName) {
    Instant issuedAt = Instant.now(clock);
    Instant expiresAt = issuedAt.plusSeconds((long) properties.ttlMinutes() * 60);

    JwtClaimsSet claims = buildClaims(meetingId, room, subject, role, guest, displayName, issuedAt, expiresAt);
    JwsHeader headers = JwsHeader.with(resolveMacAlgorithm())
      .type(JOSEObjectType.JWT.getType())
      .build();
    String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    return new MeetingTokenIssuer.AccessTokenResult(tokenValue, expiresAt, role.value);
  }

  private JwtClaimsSet buildClaims(
      String meetingId,
      String room,
      String subject,
      MeetingRole role,
      boolean guest,
      String displayName,
      Instant issuedAt,
      Instant expiresAt) {
    JwtClaimsSet.Builder claimsBuilder =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim("meetingId", meetingId)
            .claim("room", room)
            .claim(properties.roleClaimName(), role.value)
            .claim("guest", guest);

    if (displayName != null && !displayName.isBlank()) {
      claimsBuilder
          .claim("name", displayName)
          .claim("context", Map.of("user", Map.of("name", displayName)));
    }

    return claimsBuilder.build();
  }

  private MacAlgorithm resolveMacAlgorithm() {
    try {
      return algorithmPolicy.resolveMacAlgorithmForSecret(properties.algorithm());
    } catch (IllegalArgumentException ex) {
      throw new MeetingTokenException(
          org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
          "CONFIG_INCOMPATIBLE",
          "Неподдерживаемый алгоритм подписи access-токена.",
          ex);
    }
  }
}