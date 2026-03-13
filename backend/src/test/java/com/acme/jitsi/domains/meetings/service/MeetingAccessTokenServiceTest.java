package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
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
      dbAssignmentPolicy(),
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
          Mockito.mock(MeetingProfilesPort.class),
          encoder,
          DEFAULT_JWT_ALGORITHM_POLICY,
          Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueToken("meeting-a", "u-host");
    String token = extractToken(result.joinUrl());
    SignedJWT jwt = SignedJWT.parse(token);

    assertThat(jwt.getJWTClaimsSet().getStringClaim("affiliation")).isEqualTo("host");
    assertThat(jwt.getJWTClaimsSet().getClaim("role")).isNull();
  }

  @Test
  void issuedMeetingTokenUsesJwtTypeHeaderExpectedByJitsi() throws Exception {
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
        Mockito.mock(MeetingProfilesPort.class),
        encoder,
        DEFAULT_JWT_ALGORITHM_POLICY,
        Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueToken("meeting-a", "u-host");
    String token = extractToken(result.joinUrl());
    SignedJWT jwt = SignedJWT.parse(token);

    assertThat(jwt.getHeader().getType()).isNotNull();
    assertThat(jwt.getHeader().getType().getType()).isEqualTo("JWT");
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
          Mockito.mock(MeetingProfilesPort.class),
          encoder,
          DEFAULT_JWT_ALGORITHM_POLICY,
          Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueToken("meeting-a", "u-participant");
    String token = extractToken(result.joinUrl());

    assertThat(output.getOut()).contains("meeting_access_token_issued");
    assertThat(output.getOut()).contains("meeting-a");
    assertThat(output.getOut()).contains("participant");
    assertThat(output.getOut()).doesNotContain(token);
    assertThat(output.getOut()).doesNotContain("u-participant");
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
          Mockito.mock(MeetingProfilesPort.class),
          encoder,
          DEFAULT_JWT_ALGORITHM_POLICY,
          Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
          Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueToken("meeting-missing", "u-participant"))
        .isInstanceOf(MeetingTokenException.class)
        .hasMessageContaining("Встреча не найдена");

    assertThat(output.getOut()).contains("meeting_access_token_issue_failed");
    assertThat(output.getOut()).contains(ErrorCode.MEETING_NOT_FOUND.code());
    assertThat(output.getOut()).contains("meeting-missing");
    assertThat(output.getOut()).doesNotContain("u-participant");
  }

  @Test
  void issueTokenBuildsJoinUrlFromMeetingTitleAndProfileDisplayName() throws Exception {
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
    assignment.setSubject("u-host");
    assignment.setRole("host");
    properties.setAssignments(java.util.List.of(assignment));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);
    JwtEncoder encoder = new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties);
    MeetingService meetingService = Mockito.mock(MeetingService.class);
    MeetingProfilesPort meetingProfilesPort = Mockito.mock(MeetingProfilesPort.class);
    TokenIssuanceCompatibilityPolicy compatibilityPolicy = Mockito.mock(TokenIssuanceCompatibilityPolicy.class);

    Mockito.when(meetingService.getMeeting("meeting-a"))
        .thenReturn(Meeting.builder().meetingId("meeting-a").title("Тестовая комната").build());
    Mockito.when(meetingProfilesPort.findBySubjectId("u-host"))
      .thenReturn(new MeetingProfileSnapshot("u-host", "Иван Иванов", "Acme", "Engineer"));

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        allowAllMeetingStateGuard(),
        properties,
        meetingService,
        meetingProfilesPort,
        encoder,
        DEFAULT_JWT_ALGORITHM_POLICY,
        compatibilityPolicy,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueToken("meeting-a", "u-host");
    String token = extractToken(result.joinUrl());
    SignedJWT jwt = SignedJWT.parse(token);

  String decodedJoinUrl = URLDecoder.decode(result.joinUrl(), StandardCharsets.UTF_8);

  assertThat(decodedJoinUrl).contains("/тестовая-комната#jwt=");
  assertThat(decodedJoinUrl)
    .contains("userInfo.displayName=\"Иван Иванов\"")
    .contains("config.defaultLocalDisplayName=\"Иван Иванов\"");
    assertThat(jwt.getJWTClaimsSet().getStringClaim("room")).isEqualTo("%d1%82%d0%b5%d1%81%d1%82%d0%be%d0%b2%d0%b0%d1%8f-%d0%ba%d0%be%d0%bc%d0%bd%d0%b0%d1%82%d0%b0");
  }

  @Test
  void issueGuestTokenUsesSharedPathWithoutMeetingStateGuard() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setTtlMinutes(20);
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setRoleClaimName("role");
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");

    MeetingRoleResolver resolver = Mockito.mock(MeetingRoleResolver.class);
    MeetingStateGuard stateGuard = Mockito.mock(MeetingStateGuard.class);
    JwtEncoder encoder = new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties);

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        stateGuard,
        properties,
        Mockito.mock(MeetingService.class),
        Mockito.mock(MeetingProfilesPort.class),
        encoder,
        DEFAULT_JWT_ALGORITHM_POLICY,
        Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.TokenResult result = service.issueGuestToken("meeting-a", "guest-1");

    assertThat(result.role()).isEqualTo("participant");
    verify(stateGuard, never()).assertJoinAllowed("meeting-a");
    verify(resolver, never()).resolve("meeting-a", "guest-1");
  }

  @Test
  void issueAccessTokenPreservesConfigIncompatibleContractWhenCompatibilityBlocksIssuance() {
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setIssuer("https://portal.example.test");
    properties.setAudience("jitsi-meet");
    properties.setTtlMinutes(20);
    properties.setSigningSecret("01234567890123456789012345678901");
    properties.setRoleClaimName("role");
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");

    MeetingRoleResolver resolver = Mockito.mock(MeetingRoleResolver.class);
    MeetingStateGuard stateGuard = Mockito.mock(MeetingStateGuard.class);
    TokenIssuanceCompatibilityPolicy compatibilityPolicy = Mockito.mock(TokenIssuanceCompatibilityPolicy.class);
    doThrow(new TokenIssuancePolicyException(
        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CONFIG_INCOMPATIBLE.code(),
      "Token issuance is blocked due to incompatible active config set: cs-1"))
        .when(compatibilityPolicy)
        .assertTokenIssuanceAllowed();

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        stateGuard,
        properties,
        Mockito.mock(MeetingService.class),
        Mockito.mock(MeetingProfilesPort.class),
        new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties),
        DEFAULT_JWT_ALGORITHM_POLICY,
        compatibilityPolicy,
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueAccessToken("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFIG_INCOMPATIBLE.code());
          assertThat(exception.getMessage()).contains("Token issuance is blocked");
        });

    verify(stateGuard, never()).assertJoinAllowed(anyString());
    verify(resolver, never()).resolve(anyString(), anyString());
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
      Mockito.mock(MeetingProfilesPort.class),
      encoder,
      DEFAULT_JWT_ALGORITHM_POLICY,
      Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueAccessToken("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFIG_INCOMPATIBLE.code());
          assertThat(exception.getMessage()).isEqualTo("Неподдерживаемый алгоритм подписи access-токена.");
        });
  }

  @Test
  void issueAccessTokenDoesNotResolveDisplayNameOrJoinUrlData() {
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
    assignment.setSubject("u-host");
    assignment.setRole("host");
    properties.setAssignments(java.util.List.of(assignment));

    MeetingRoleResolver resolver = new MeetingRoleResolver(properties, DEFAULT_POLICIES);
    JwtEncoder encoder = new MeetingTokenConfig(DEFAULT_JWT_ALGORITHM_POLICY).meetingJwtEncoder(properties);
    MeetingService meetingService = Mockito.mock(MeetingService.class);
    MeetingProfilesPort meetingProfilesPort = Mockito.mock(MeetingProfilesPort.class);

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        allowAllMeetingStateGuard(),
        properties,
        meetingService,
      meetingProfilesPort,
        encoder,
        DEFAULT_JWT_ALGORITHM_POLICY,
        Mockito.mock(TokenIssuanceCompatibilityPolicy.class),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    MeetingTokenIssuer.AccessTokenResult result = service.issueAccessToken("meeting-a", "u-host");

    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.role()).isEqualTo("host");
    verify(meetingProfilesPort, never()).findBySubjectId("u-host");
    verify(meetingService).getMeeting("meeting-a");
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
      Mockito.when(meetingRepository.existsById(anyString())).thenReturn(false);
      return new UnknownMeetingMeetingRoleResolutionPolicy(meetingRepository);
    }

    private static MeetingRoleResolutionPolicy dbAssignmentPolicy() {
      MeetingParticipantAssignmentRepository assignmentRepository = Mockito.mock(MeetingParticipantAssignmentRepository.class);
      Mockito.when(assignmentRepository.findByMeetingIdAndSubjectId(anyString(), anyString()))
          .thenReturn(Optional.empty());
      return new DbParticipantAssignmentMeetingRoleResolutionPolicy(assignmentRepository);
    }

    private static MeetingStateGuard allowAllMeetingStateGuard() {
      return Mockito.mock(MeetingStateGuard.class);
    }
}
