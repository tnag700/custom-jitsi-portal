package com.acme.jitsi.domains.meetings.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class MeetingAccessTokenControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void unauthenticatedRequestReturns401() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .with(csrf())
            .header("X-Trace-Id", "trace-auth-1"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/meetings/meeting-a/access-token"))
      .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"))
      .andExpect(jsonPath("$.properties.traceId").value("trace-auth-1"));
  }

  @Test
  void missingCsrfReturns403WithStableErrorContract() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .header("X-Trace-Id", "trace-csrf-1")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-participant"))))
        .andExpect(status().isForbidden())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/meetings/meeting-a/access-token"))
        .andExpect(jsonPath("$.properties.errorCode").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-csrf-1"));
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
      .andExpect(jsonPath("$.properties.errorCode").value("ACCESS_DENIED"))
      .andExpect(jsonPath("$.properties.traceId").value("trace-123"));
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
        .andExpect(jsonPath("$.properties.errorCode").value("MEETING_NOT_FOUND"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-meeting-1-not-found"));
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
      .andExpect(jsonPath("$.properties.errorCode").value("ROLE_MISMATCH"))
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
      .andExpect(jsonPath("$.properties.errorCode").value("ROLE_MISMATCH"))
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
        .andExpect(jsonPath("$.properties.errorCode").value("ROLE_MISMATCH"))
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
        .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"));

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
        .andExpect(jsonPath("$.properties.errorCode").value("ROLE_MISMATCH"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-role-log-1"));

    assertThat(output.getOut()).contains("join_clicked meetingId=meeting-conflict subject=u-conflict");
    assertThat(output.getOut()).contains("join_failed status=409 code=ROLE_MISMATCH");
    assertThat(output.getOut()).contains("trace-role-log-1");
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
        .andExpect(jsonPath("$.properties.errorCode").value("MEETING_CANCELED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-meeting-canceled-1"));
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
        .andExpect(jsonPath("$.properties.errorCode").value("MEETING_ENDED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-meeting-ended-1"));
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
    assertThat(joinUrl).contains("mariningorskaya-tsrb");
    assertThat(joinUrl).contains("userInfo.displayName=");
    assertThat(joinUrl).contains("config.defaultLocalDisplayName=");
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
