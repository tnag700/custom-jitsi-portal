package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenFlowCompatibilityGuard;
import com.acme.jitsi.domains.profiles.service.UserProfileService;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.oauth2.jwt.JwtEncoder;

@ExtendWith(OutputCaptureExtension.class)
class MeetingAccessTokenServiceTest {
  private static final java.util.List<MeetingRoleResolutionPolicy> DEFAULT_POLICIES = java.util.List.of(
      new BlockedSubjectMeetingRoleResolutionPolicy(),
      unknownMeetingPolicy(),
      new ExplicitAssignmentMeetingRoleResolutionPolicy(),
      new UnknownRolePolicyMeetingRoleResolutionPolicy());
  private static final DefaultJwtAlgorithmPolicy DEFAULT_JWT_ALGORITHM_POLICY = new DefaultJwtAlgorithmPolicy();

  @Test
  void rejectsQueryBasedJwtJoinUrlTemplate() {
    MeetingTokenProperties properties = new MeetingTokenProperties();

    assertThatThrownBy(() -> properties.setJoinUrlTemplate("https://meet.example/%s?jwt=%s"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not place jwt in query");
  }

  @Test
  void usesConfiguredRoleClaimNameInIssuedToken() throws Exception {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setTtlMinutes(20);
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setRoleClaimName("affiliation");
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");
    properties.setKnownMeetingIds(java.util.List.of("meeting-a"));

    MeetingTokenProperties.RoleAssignment assignment = new MeetingTokenProperties.RoleAssignment();
    assignment.setMeetingId("meeting-a");
    assignment.setSubject("u-host");
    assignment.setRole("host");
    properties.setAssignments(java.util.List.of(assignment));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);
    JwtEncoder encoder = new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties);
      MeetingAccessTokenService service = new MeetingAccessTokenService(
          resolver,
          allowAllMeetingStateGuard(),
          properties,
          Mockito.mock(MeetingService.class),
          Mockito.mock(UserProfileService.class),
          encoder,
          DEFAULT_JWT_ALGORITHM_POLICY,
          Mockito.mock(TokenFlowCompatibilityGuard.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueToken("meeting-a", "u-host");
    String token = extractToken(result.joinUrl());
    SignedJWT jwt = SignedJWT.parse(token);

    assertThat(jwt.getJWTClaimsSet().getStringClaim("affiliation")).isEqualTo("host");
    assertThat(jwt.getJWTClaimsSet().getClaim("role")).isNull();
  }

  @Test
  void logsSuccessfulTokenIssuanceWithoutRawTokenLeak(CapturedOutput output) {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setTtlMinutes(20);
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setRoleClaimName("role");
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");
    properties.setKnownMeetingIds(java.util.List.of("meeting-a"));

    MeetingTokenProperties.RoleAssignment assignment = new MeetingTokenProperties.RoleAssignment();
    assignment.setMeetingId("meeting-a");
    assignment.setSubject("u-participant");
    assignment.setRole("participant");
    properties.setAssignments(java.util.List.of(assignment));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);
    JwtEncoder encoder = new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties);
      MeetingAccessTokenService service = new MeetingAccessTokenService(
          resolver,
          allowAllMeetingStateGuard(),
          properties,
          Mockito.mock(MeetingService.class),
          Mockito.mock(UserProfileService.class),
          encoder,
          DEFAULT_JWT_ALGORITHM_POLICY,
          Mockito.mock(TokenFlowCompatibilityGuard.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueToken("meeting-a", "u-participant");
    String token = extractToken(result.joinUrl());

    assertThat(output.getOut()).contains("meeting_access_token_issued");
    assertThat(output.getOut()).contains("meeting-a");
    assertThat(output.getOut()).contains("u-participant");
    assertThat(output.getOut()).contains("participant");
    assertThat(output.getOut()).doesNotContain(token);
  }

  @Test
  void logsFailureWithStableErrorCodeWhenMeetingUnknown(CapturedOutput output) {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setTtlMinutes(20);
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setRoleClaimName("role");
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");
    properties.setKnownMeetingIds(java.util.List.of("meeting-a"));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);
    JwtEncoder encoder = new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties);
      MeetingAccessTokenService service = new MeetingAccessTokenService(
          resolver,
          allowAllMeetingStateGuard(),
          properties,
          Mockito.mock(MeetingService.class),
          Mockito.mock(UserProfileService.class),
          encoder,
          DEFAULT_JWT_ALGORITHM_POLICY,
          Mockito.mock(TokenFlowCompatibilityGuard.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueToken("meeting-missing", "u-participant"))
        .isInstanceOf(MeetingTokenException.class)
        .hasMessageContaining("Встреча не найдена");

    assertThat(output.getOut()).contains("meeting_access_token_issue_failed");
    assertThat(output.getOut()).contains("MEETING_NOT_FOUND");
    assertThat(output.getOut()).contains("meeting-missing");
    assertThat(output.getOut()).contains("u-participant");
  }

  @Test
  void keepsUnsupportedAlgorithmErrorContractForAccessTokenIssuance() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setAlgorithm("RS256");
    properties.setTtlMinutes(20);
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setRoleClaimName("role");
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");
    properties.setKnownMeetingIds(java.util.List.of("meeting-a"));

    MeetingTokenProperties.RoleAssignment assignment = new MeetingTokenProperties.RoleAssignment();
    assignment.setMeetingId("meeting-a");
    assignment.setSubject("u-host");
    assignment.setRole("host");
    properties.setAssignments(java.util.List.of(assignment));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);
    JwtEncoder encoder = Mockito.mock(JwtEncoder.class);
    MeetingAccessTokenService service = new MeetingAccessTokenService(
      resolver,
      allowAllMeetingStateGuard(),
      properties,
      Mockito.mock(MeetingService.class),
      Mockito.mock(UserProfileService.class),
      encoder,
      DEFAULT_JWT_ALGORITHM_POLICY,
      Mockito.mock(TokenFlowCompatibilityGuard.class),
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueAccessToken("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.errorCode()).isEqualTo("CONFIG_INCOMPATIBLE");
          assertThat(exception.getMessage()).isEqualTo("Неподдерживаемый алгоритм подписи access-токена.");
        });
  }

  private String extractToken(String joinUrl) {
    URI uri = URI.create(joinUrl);
    String query = uri.getQuery();
    if (query != null) {
      String token = extractJwtParam(query);
      if (token != null) {
        return token;
      }
    }

    String fragment = uri.getFragment();
    if (fragment != null) {
      String token = extractJwtParam(fragment);
      if (token != null) {
        return token;
      }
    }

    throw new IllegalStateException("JWT token is missing in joinUrl");
  }

  private String extractJwtParam(String params) {
    for (String pair : params.split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && "jwt".equals(parts[0])) {
        String decoded = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        if (decoded.length() >= 2 && decoded.startsWith("\"") && decoded.endsWith("\"")) {
          return decoded.substring(1, decoded.length() - 1);
        }
        return decoded;
      }
    }
    return null;
  }

    private static MeetingRoleResolutionPolicy unknownMeetingPolicy() {
      MeetingRepository meetingRepository = Mockito.mock(MeetingRepository.class);
      Mockito.when(meetingRepository.existsById(Mockito.anyString())).thenReturn(false);
      return new UnknownMeetingMeetingRoleResolutionPolicy(meetingRepository);
    }

    private static MeetingStateGuard allowAllMeetingStateGuard() {
      return Mockito.mock(MeetingStateGuard.class);
    }
}
