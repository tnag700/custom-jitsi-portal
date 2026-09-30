package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import com.acme.jitsi.domains.meetings.event.MeetingJoinObservedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingJoinObservabilityPublisher;
import com.acme.jitsi.domains.meetings.service.MeetingService;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
  "spring.datasource.url=jdbc:h2:mem:testdb-meeting-access-token;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "app.security.sso.expected-issuer=https://issuer.example.test",
      JwtTestProperties.TOKEN_SIGNING_SECRET,
      JwtTestProperties.TOKEN_ISSUER,
      JwtTestProperties.TOKEN_AUDIENCE,
      JwtTestProperties.TOKEN_ALGORITHM,
      JwtTestProperties.TOKEN_TTL_MINUTES,
      JwtTestProperties.TOKEN_ROLE_CLAIM_NAME,
      "app.meetings.token.unknown-role-policy=fallback-participant",
      "app.auth.refresh.idle-ttl-minutes=60",
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES,
      "app.rooms.valid-config-sets=config-1,config-2",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role",
      "app.rooms.config-sets.config-2.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-2.audience=jitsi-meet",
      "app.rooms.config-sets.config-2.role-claim=role",
      "app.meetings.token.known-meeting-ids=meeting-a,meeting-b,meeting-c,meeting-conflict",
      "app.meetings.token.blocked-subjects=u-blocked",
      "app.meetings.token.assignments[0].meeting-id=meeting-b",
      "app.meetings.token.assignments[0].subject=u-host",
      "app.meetings.token.assignments[0].role=host",
      "app.meetings.token.assignments[1].meeting-id=meeting-c",
      "app.meetings.token.assignments[1].subject=u-mod",
      "app.meetings.token.assignments[1].role=moderator",
      "app.meetings.token.assignments[2].meeting-id=meeting-conflict",
      "app.meetings.token.assignments[2].subject=u-conflict",
      "app.meetings.token.assignments[2].role=host",
      "app.meetings.token.assignments[3].meeting-id=meeting-conflict",
      "app.meetings.token.assignments[3].subject=u-conflict",
      "app.meetings.token.assignments[3].role=moderator",
      "app.meetings.token.assignments[4].meeting-id=meeting-b",
      "app.meetings.token.assignments[4].subject=u-bad-config",
      "app.meetings.token.assignments[4].role=admin"
    })
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(MeetingAccessTokenControllerTest.FixedClockConfiguration.class)
class MeetingAccessTokenControllerTest {

  private static final Instant TEST_NOW = Instant.parse("2026-01-15T12:00:00Z");

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClockConfiguration {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(TEST_NOW, ZoneOffset.UTC);
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ProblemResponseFacade problemResponseFacade;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    jdbcTemplate.execute("DELETE FROM meeting_audit_events");
  }

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-auth-1"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/meetings/meeting-a/access-token"))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()))
      .andExpect(jsonPath("$.properties.requestId").value("trace-auth-1"));
  }

  @Test
  void missingCsrfReturns403WithStableErrorContract() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .header("X-Trace-Id", "trace-csrf-1")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-participant"))))
        .andExpect(status().isForbidden())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/meetings/meeting-a/access-token"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-csrf-1"));
  }

  @Test
  void blockedSubjectReturns403WithStableErrorCodeAndTraceId() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-123")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-blocked"))))
        .andExpect(status().isForbidden())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/meetings/meeting-a/access-token"))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
      .andExpect(jsonPath("$.properties.requestId").value("trace-123"));
  }

  @Test
  void numericMeetingIdNotFoundReturns404WithMeetingNotFoundCode() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/1/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-meeting-1-not-found")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-participant"))))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value("/api/v1/meetings/1/access-token"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_NOT_FOUND.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-meeting-1-not-found"));
  }

  @Test
  void noExplicitAssignmentReturnsParticipantRoleAndRequiredClaims() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-participant"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("participant"))
        .andExpect(jsonPath("$.joinUrl").isString())
        .andExpect(jsonPath("$.expiresAt").isNotEmpty())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    String token = extractToken(JsonPath.parse(body).read("$.joinUrl", String.class));
    SignedJWT signedJwt = SignedJWT.parse(token);

    assertThat(signedJwt.getJWTClaimsSet().getIssuer()).isEqualTo("https://portal.example.test");
    assertThat(signedJwt.getJWTClaimsSet().getAudience()).containsExactly("jitsi-meet");
    assertThat(signedJwt.getJWTClaimsSet().getSubject()).isEqualTo("u-participant");
    assertThat(signedJwt.getJWTClaimsSet().getStringClaim("meetingId")).isEqualTo("meeting-a");
    assertThat(signedJwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("participant");
    assertThat(signedJwt.getJWTClaimsSet().getIssueTime()).isNotNull();
    assertThat(signedJwt.getJWTClaimsSet().getExpirationTime()).isNotNull();
    assertThat(signedJwt.getJWTClaimsSet().getJWTID()).isNotBlank();

    Instant issuedAt = signedJwt.getJWTClaimsSet().getIssueTime().toInstant();
    Instant expiresAt = signedJwt.getJWTClaimsSet().getExpirationTime().toInstant();
    long lifetimeMinutes = (expiresAt.getEpochSecond() - issuedAt.getEpochSecond()) / 60;
    assertThat(lifetimeMinutes).isBetween(15L, 30L);
  }

  @ParameterizedTest
  @CsvSource({
    "meeting-b,u-host,host",
    "meeting-c,u-mod,moderator"
  })
  void explicitRoleAssignmentIsUsedInTokenClaim(String meetingId, String subject, String expectedRole) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/meetings/{meetingId}/access-token", meetingId)
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", subject))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value(expectedRole))
        .andReturn();

    String body = result.getResponse().getContentAsString();
    String token = extractToken(JsonPath.parse(body).read("$.joinUrl", String.class));
    SignedJWT signedJwt = SignedJWT.parse(token);
    assertThat(signedJwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo(expectedRole);
  }

  @Test
  void ambiguousRoleMappingReturnsProblemDetailWithRoleMismatch() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/meetings/meeting-conflict/access-token")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-conflict"))))
        .andExpect(status().isConflict())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/meetings/meeting-conflict/access-token"))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ROLE_MISMATCH.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
      .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("eyJ");
  }

  @Test
  void invalidConfiguredRoleReturnsProblemDetailWithRoleMismatch() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/meetings/meeting-b/access-token")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-bad-config"))))
        .andExpect(status().isConflict())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ROLE_MISMATCH.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
      .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("eyJ");
  }

  @ParameterizedTest
  @CsvSource({
      "meeting-conflict,u-conflict",
      "meeting-b,u-bad-config"
  })
  void roleMappingErrorsUseCanonicalRoleMismatchCode(String meetingId, String subject) throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/meetings/{meetingId}/access-token", meetingId)
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", subject))))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value("/api/v1/meetings/%s/access-token".formatted(meetingId)))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ROLE_MISMATCH.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain("eyJ");
  }

  @Test
  void unauthenticatedRequestWritesSecurityErrorLogs(CapturedOutput output) throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-auth-log-1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()));

    assertThat(output.getOut()).contains("authentication_required");
    assertThat(output.getOut()).contains("problem_response status=401 code=AUTH_REQUIRED");
    assertThat(output.getOut()).contains("trace-auth-log-1");
  }

  @Test
  void roleMismatchWritesMeetingTokenErrorLogWithTraceId(CapturedOutput output) throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-conflict/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-role-log-1")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-conflict"))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ROLE_MISMATCH.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-role-log-1"));

    assertThat(output.getOut()).contains("join_clicked meetingId=meeting-conflict subject=u-conflict");
    assertThat(output.getOut()).contains("eventType=MEETING_JOIN_FAILED");
    assertThat(output.getOut()).contains("result=fail");
    assertThat(output.getOut()).contains("reasonCategory=ROLE");
    assertThat(output.getOut()).contains("join_failed status=409 code=" + ErrorCode.ROLE_MISMATCH.code());
    assertThat(output.getOut()).contains("traceId=");
  }

  @ParameterizedTest
  @CsvSource(value = {
      "ROLE_MISMATCH, ROLE", "ROLE_CONFLICT, ROLE", "MEETING_ROLE_CONFLICT, ROLE",
      "CONFIG_INCOMPATIBLE, CONFIG", "TOKEN_INVALID, TOKEN", "TOKEN_REVOKED, TOKEN",
      "AUTH_REQUIRED, TOKEN", "ACCESS_DENIED, SSO", "UNRECOGNIZED, UNKNOWN", "NULL, UNKNOWN"
  }, nullValues = "NULL")
  void joinFailureEventAndLogUseTheSameCategory(String errorCode, String category, CapturedOutput output) {
    MeetingService meetings = mock(MeetingService.class);
    when(meetings.getMeeting("classifier-meeting")).thenReturn(observedMeeting("classifier-room"));
    List<MeetingJoinObservedEvent> events = new ArrayList<>();
    MeetingJoinObservabilityPublisher publisher = new MeetingJoinObservabilityPublisher(
        event -> events.add((MeetingJoinObservedEvent) event), meetings, Clock.fixed(TEST_NOW, ZoneOffset.UTC));
    MeetingTokenIssuer issuer = mock(MeetingTokenIssuer.class);
    MeetingTokenException failure = new MeetingTokenException(HttpStatus.CONFLICT, errorCode, "Join failure");
    when(issuer.issueToken("classifier-meeting", "classifier-subject")).thenThrow(failure);
    MeetingAccessTokenController controller = new MeetingAccessTokenController(issuer, publisher, problemResponseFacade);
    OAuth2User principal = mock(OAuth2User.class);
    when(principal.getName()).thenReturn("classifier-subject");
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/meetings/classifier-meeting/access-token");
    request.addHeader("X-Trace-Id", "trace-classifier");

    assertThatThrownBy(() -> controller.issueAccessToken("classifier-meeting", principal, request)).isSameAs(failure);

    assertThat(events).hasSize(1);
    MeetingJoinObservedEvent event = events.get(0);
    assertThat(event).isEqualTo(new MeetingJoinObservedEvent(
        "MEETING_JOIN_FAILED", "fail", "classifier-meeting", "classifier-room", "classifier-subject",
        null, errorCode, category, "trace-classifier", event.durationMs(), TEST_NOW));
    assertThat(event.durationMs()).isGreaterThanOrEqualTo(0L);
    assertThat(output.getOut()).contains(
        "meeting_join_event eventType=MEETING_JOIN_FAILED result=fail meetingId=classifier-meeting"
            + " subjectId=classifier-subject errorCode=" + errorCode + " reasonCategory=" + event.reasonCategory()
            + " durationMs=" + event.durationMs() + " traceId=trace-classifier requestId=trace-classifier");
  }

  @ParameterizedTest
  @ValueSource(strings = {"lookup-failure", "missing-meeting", "null-room", "blank-room"})
  void failurePublicationSurvivesMissingMeetingMetadata(String metadata) {
    MeetingService meetings = mock(MeetingService.class);
    switch (metadata) {
      case "lookup-failure" -> when(meetings.getMeeting("classifier-meeting")).thenThrow(new IllegalStateException("Lookup failed"));
      case "missing-meeting" -> when(meetings.getMeeting("classifier-meeting")).thenReturn(null);
      case "null-room" -> when(meetings.getMeeting("classifier-meeting")).thenReturn(observedMeeting(null));
      case "blank-room" -> when(meetings.getMeeting("classifier-meeting")).thenReturn(observedMeeting(" "));
      default -> throw new IllegalArgumentException(metadata);
    }
    List<MeetingJoinObservedEvent> events = new ArrayList<>();
    MeetingJoinObservabilityPublisher publisher = new MeetingJoinObservabilityPublisher(
        event -> events.add((MeetingJoinObservedEvent) event), meetings, Clock.fixed(TEST_NOW, ZoneOffset.UTC));

    publisher.publishFailure("classifier-meeting", "classifier-subject", "trace-classifier", 812L, "TOKEN_INVALID");

    assertThat(events).containsExactly(new MeetingJoinObservedEvent(
        "MEETING_JOIN_FAILED", "fail", "classifier-meeting", null, "classifier-subject",
        null, "TOKEN_INVALID", "TOKEN", "trace-classifier", 812L, TEST_NOW));
  }

  @Test
  void failurePublicationPropagatesPublisherException() {
    IllegalStateException failure = new IllegalStateException("Publication failed");
    MeetingJoinObservabilityPublisher publisher = new MeetingJoinObservabilityPublisher(
        event -> { throw failure; }, mock(MeetingService.class), Clock.fixed(TEST_NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> publisher.publishFailure(
        "classifier-meeting", "classifier-subject", "trace-classifier", 812L, "TOKEN_INVALID")).isSameAs(failure);
  }

  private Meeting observedMeeting(String roomId) {
    return new Meeting("classifier-meeting", roomId, "Classifier meeting", null, "scheduled", "config-1",
        MeetingStatus.SCHEDULED, TEST_NOW, TEST_NOW.plusSeconds(3600), true, false, TEST_NOW, TEST_NOW);
  }

  @Test
  void successfulJoinWritesCanonicalStructuredEventAndAuditEntry(CapturedOutput output) throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-b/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-join-success-1")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-host"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("host"));

    assertThat(output.getOut()).contains("eventType=MEETING_JOIN_SUCCEEDED");
    assertThat(output.getOut()).contains("result=success");
    assertThat(output.getOut()).contains("meetingId=meeting-b");
    assertThat(output.getOut()).contains("subjectId=u-host");
    assertThat(output.getOut()).contains("trace-join-success-1");

    Integer count = awaitCount(
        "SELECT COUNT(*) FROM meeting_audit_events WHERE action_type = ? AND meeting_id = ? AND subject_id = ?",
        "join_success",
        "meeting-b",
        "u-host");

    assertThat(count).isEqualTo(1);
  }

  private Integer awaitCount(String sql, Object... args) {
    long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
    Integer count = 0;
    while (System.nanoTime() < deadline) {
      count = jdbcTemplate.queryForObject(sql, Integer.class, args);
      if (count != null && count > 0) {
        return count;
      }
      try {
        Thread.sleep(50);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for meeting audit event", ex);
      }
    }
    return count;
  }

  @Test
  void canceledMeetingJoinReturnsMeetingCanceledErrorCode() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Token Room 1",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");
    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Cancelable token meeting",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/cancel", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/access-token", meetingId)
            .with(csrf())
            .header("X-Trace-Id", "trace-meeting-canceled-1")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-participant"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_CANCELED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-meeting-canceled-1"));

    Integer count = awaitCount(
      "SELECT COUNT(*) FROM meeting_audit_events WHERE action_type = ? AND meeting_id = ? AND subject_id = ?",
      "join_failed",
      meetingId,
      "u-participant");

    assertThat(count).isEqualTo(1);
  }

  @Test
  void endedMeetingJoinReturnsMeetingEndedErrorCode() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Token Room 2",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");
    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Ended token meeting",
                  "meetingType": "scheduled",
                  "startsAt": "2024-01-17T10:00:00Z",
                  "endsAt": "2024-01-17T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/access-token", meetingId)
            .with(csrf())
            .header("X-Trace-Id", "trace-meeting-ended-1")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-participant"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_ENDED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-meeting-ended-1"));
  }

  @Test
  void generatedJoinUrlUsesMeetingTitleAndProfileDisplayName() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Readable Room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Маринингорская ЦРБ",
                  "meetingType": "scheduled",
                  "startsAt": "2026-06-17T10:00:00Z",
                  "endsAt": "2026-06-17T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "u-profiled", "role": "host" }
                """))
        .andExpect(status().isCreated());

    jdbcTemplate.update(
      "INSERT INTO user_profiles (id, subject_id, tenant_id, full_name, organization, position, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP())",
      java.util.UUID.randomUUID().toString(),
      "u-profiled",
      "tenant-1",
      "Иванов Иван Иванович",
      "Маринингорская ЦРБ",
      "Врач");

    MvcResult result = mockMvc.perform(post("/api/v1/meetings/{meetingId}/access-token", meetingId)
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> {
              attrs.put("sub", "u-profiled");
              attrs.put("tenantId", "tenant-1");
            })))
        .andExpect(status().isOk())
        .andReturn();

    String joinUrl = JsonPath.parse(result.getResponse().getContentAsString()).read("$.joinUrl", String.class);
    String decodedJoinUrl = java.net.URLDecoder.decode(joinUrl, java.nio.charset.StandardCharsets.UTF_8);

    assertThat(decodedJoinUrl).contains(meetingId.toLowerCase(java.util.Locale.ROOT));
    assertThat(decodedJoinUrl).contains("userInfo.displayName=\"Иванов Иван Иванович\"");
    assertThat(decodedJoinUrl).contains("config.defaultLocalDisplayName=\"Иванов Иван Иванович\"");
  }

  private String extractToken(String joinUrl) {
    URI uri = URI.create(joinUrl);
    String query = uri.getQuery();
    String fragment = uri.getFragment();

    if (query != null) {
      String tokenFromQuery = extractTokenPart(query);
      if (tokenFromQuery != null) {
        return tokenFromQuery;
      }
    }

    if (fragment != null) {
      String tokenFromFragment = extractTokenPart(fragment);
      if (tokenFromFragment != null) {
        return tokenFromFragment;
      }
    }

    throw new IllegalStateException("JWT token is missing in joinUrl");
  }

  private String extractTokenPart(String serializedParams) {
    for (String pair : serializedParams.split("&")) {
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
}
