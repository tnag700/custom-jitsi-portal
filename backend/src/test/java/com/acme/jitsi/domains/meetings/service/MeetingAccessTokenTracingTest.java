package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import com.acme.jitsi.shared.observability.RecordedObservationHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingAccessTokenTracingTest {

  private final RecordedObservationHandler observations = new RecordedObservationHandler();

  private MeetingTokenProperties properties;
  private MeetingRoleResolver resolver;
  private MeetingStateGuard stateGuard;
  private TokenIssuanceCompatibilityPolicy compatibilityPolicy;

  @BeforeEach
  void setUp() {
    properties = new MeetingTokenProperties();
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

    resolver = mock(MeetingRoleResolver.class);
    stateGuard = mock(MeetingStateGuard.class);
    compatibilityPolicy = mock(TokenIssuanceCompatibilityPolicy.class);
    observations.reset();
  }

  @Test
  void issueAccessTokenEmitsCanonicalSuccessObservation() {
    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        stateGuard,
        properties,
        mock(MeetingService.class),
        mock(MeetingProfilesPort.class),
        new MeetingTokenConfig(new DefaultJwtAlgorithmPolicy()).meetingJwtEncoder(properties),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

      org.mockito.Mockito.when(resolver.resolve("meeting-a", "u-host")).thenReturn(MeetingRole.HOST);

    service.issueAccessToken("meeting-a", "u-host");

    RecordedObservationHandler.RecordedObservation observation = observations.only("meetings.access-token.issue");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "success")
        .containsEntry("flow.stage", "issue_token")
        .containsEntry("flow.guest", "false");
    assertThat(observation.lowCardinality().toString()).doesNotContain("u-host");
  }

  @Test
  void issueAccessTokenMarksPolicyRejectionWhenCompatibilityFails() {
    doThrow(new TokenIssuancePolicyException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CONFIG_INCOMPATIBLE.code(),
        "Token issuance is blocked due to incompatible active config set: cs-1"))
        .when(compatibilityPolicy)
        .assertTokenIssuanceAllowed();

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        stateGuard,
        properties,
        mock(MeetingService.class),
        mock(MeetingProfilesPort.class),
        new MeetingTokenConfig(new DefaultJwtAlgorithmPolicy()).meetingJwtEncoder(properties),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

      org.mockito.Mockito.when(resolver.resolve("meeting-a", "u-host")).thenReturn(MeetingRole.HOST);

    assertThatThrownBy(() -> service.issueAccessToken("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("meetings.access-token.issue");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "policy_rejection")
        .containsEntry("flow.stage", "compatibility_lookup")
        .containsEntry("flow.compatibility", "incompatible");
  }

  @Test
  void issueAccessTokenMarksMeetingStateGuardRejection() {
    doThrow(new MeetingTokenException(
        HttpStatus.CONFLICT,
        ErrorCode.MEETING_ENDED.code(),
        "Встреча завершена."))
        .when(stateGuard)
        .assertJoinAllowed("meeting-a");

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        stateGuard,
        properties,
        mock(MeetingService.class),
        mock(MeetingProfilesPort.class),
        new MeetingTokenConfig(new DefaultJwtAlgorithmPolicy()).meetingJwtEncoder(properties),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueAccessToken("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class);

    RecordedObservationHandler.RecordedObservation observation = observations.only("meetings.access-token.issue");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "policy_rejection")
        .containsEntry("flow.stage", "validate_meeting_state");
  }

  @Test
  void issueAccessTokenTranslatesUnexpectedRuntimeFailureIntoPartialFailureObservation() {
    when(resolver.resolve("meeting-a", "u-host")).thenThrow(new IllegalStateException("boom"));

    MeetingAccessTokenService service = new MeetingAccessTokenService(
        resolver,
        stateGuard,
        properties,
        mock(MeetingService.class),
        mock(MeetingProfilesPort.class),
        new MeetingTokenConfig(new DefaultJwtAlgorithmPolicy()).meetingJwtEncoder(properties),
        new DefaultJwtAlgorithmPolicy(),
        compatibilityPolicy,
        new FlowObservationFacade(observations.createRegistry()),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    assertThatThrownBy(() -> service.issueAccessToken("meeting-a", "u-host"))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException exception = (MeetingTokenException) error;
          assertThat(exception.errorCode()).isEqualTo(ErrorCode.INTERNAL_ERROR.code());
          assertThat(exception.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        });

    RecordedObservationHandler.RecordedObservation observation = observations.only("meetings.access-token.issue");
    assertThat(observation.lowCardinality())
        .containsEntry("flow.outcome", "partial_failure")
        .containsEntry("flow.stage", "resolve_role");
  }
}