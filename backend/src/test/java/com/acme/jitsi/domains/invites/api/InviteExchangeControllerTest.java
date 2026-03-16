package com.acme.jitsi.domains.invites.api;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES,
      "app.meetings.token.join-url-template=https://meet.example/%s#jwt=%s",
      "app.invites.mode=properties",
      "app.invites.exchange.atomic-store=in-memory",
      "app.invites.exchange.invites[0].token=invite-valid",
      "app.invites.exchange.invites[0].meeting-id=meeting-a",
      "app.invites.exchange.invites[0].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[0].usage-limit=10",
      "app.invites.exchange.invites[1].token=invite-onetime",
      "app.invites.exchange.invites[1].meeting-id=meeting-a",
      "app.invites.exchange.invites[1].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[1].usage-limit=1",
      "app.invites.exchange.invites[2].token=invite-expired",
      "app.invites.exchange.invites[2].meeting-id=meeting-a",
      "app.invites.exchange.invites[2].expires-at=2020-01-01T00:00:00Z",
      "app.invites.exchange.invites[2].usage-limit=1",
      "app.invites.exchange.invites[3].token=invite-closed",
      "app.invites.exchange.invites[3].meeting-id=meeting-closed",
      "app.invites.exchange.invites[3].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[3].usage-limit=1",
      "app.invites.exchange.invites[4].token=invite-canceled",
      "app.invites.exchange.invites[4].meeting-id=meeting-canceled",
      "app.invites.exchange.invites[4].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[4].usage-limit=1",
      "app.invites.exchange.invites[5].token=invite-missing-meeting",
      "app.invites.exchange.invites[5].meeting-id=meeting-unknown",
      "app.invites.exchange.invites[5].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[5].usage-limit=1",
      "app.invites.exchange.invites[6].token=invite-revoked",
      "app.invites.exchange.invites[6].meeting-id=meeting-a",
      "app.invites.exchange.invites[6].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[6].usage-limit=1",
      "app.invites.exchange.invites[6].revoked=true",
      "app.invites.exchange.invites[7].token=invite-consumed",
      "app.invites.exchange.invites[7].meeting-id=meeting-a",
      "app.invites.exchange.invites[7].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[7].usage-limit=1",
      "app.invites.exchange.invites[8].token=invite-exhausted",
      "app.invites.exchange.invites[8].meeting-id=meeting-a",
      "app.invites.exchange.invites[8].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[8].usage-limit=2",
      "app.invites.exchange.known-meeting-ids=meeting-a,meeting-closed,meeting-canceled",
      "app.invites.exchange.closed-meeting-ids=meeting-closed",
      "app.invites.exchange.canceled-meeting-ids=meeting-canceled"
    })
@AutoConfigureMockMvc
class InviteExchangeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void validInviteReturnsParticipantTokenWithGuestClaims() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteToken": "invite-valid",
                  "displayName": "Guest Host"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("participant"))
        .andExpect(jsonPath("$.meetingId").value("meeting-a"))
        .andReturn();

    String body = result.getResponse().getContentAsString();
    String token = extractToken(JsonPath.parse(body).read("$.joinUrl", String.class));
    SignedJWT jwt = SignedJWT.parse(token);
    Instant issuedAt = jwt.getJWTClaimsSet().getIssueTime().toInstant();
    Instant expiresAt = jwt.getJWTClaimsSet().getExpirationTime().toInstant();
    long lifetimeMinutes = (expiresAt.getEpochSecond() - issuedAt.getEpochSecond()) / 60;

    assertThat(jwt.getJWTClaimsSet().getStringClaim("meetingId")).isEqualTo("meeting-a");
    assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("participant");
    assertThat(jwt.getJWTClaimsSet().getBooleanClaim("guest")).isTrue();
    assertThat(lifetimeMinutes).isBetween(15L, 30L);
  }

  @Test
  void exhaustedInviteReturnsStableErrorCode() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-exhausted\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-exhausted\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-exhausted")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-exhausted\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVITE_EXHAUSTED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-exhausted"));
  }

  @Test
  void consumedInviteReturnsStableErrorCodeOnSecondUse() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-consumed\"}"))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-consumed")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-consumed\"}"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVITE_EXHAUSTED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-consumed"));
  }

      @Test
      void revokedInviteReturnsStableErrorCode() throws Exception {
        mockMvc.perform(post("/api/v1/invites/exchange")
        .with(csrf())
        .header("X-Trace-Id", "trace-revoked")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{" + "\"inviteToken\":\"invite-revoked\"}"))
          .andExpect(status().isGone())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVITE_REVOKED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-revoked"));
      }

  @Test
  void expiredInviteReturnsStableErrorCode() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-expired")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-expired\"}"))
        .andExpect(status().isGone())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVITE_EXPIRED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-expired"));
  }

  @Test
  void closedAndMissingMeetingReturnStableErrorCodes() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-closed\"}"))
        .andExpect(status().isConflict())
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_ENDED.code()));

    mockMvc.perform(post("/api/v1/invites/exchange")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{" + "\"inviteToken\":\"invite-canceled\"}"))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_CANCELED.code()));

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-missing-meeting\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_NOT_FOUND.code()));
  }

  @Test
  void guestFlowCannotEscalateRole() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteToken": "invite-valid",
                  "displayName": "moderator"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("participant"))
        .andReturn();

    String body = result.getResponse().getContentAsString();
    String token = extractToken(JsonPath.parse(body).read("$.joinUrl", String.class));
    SignedJWT jwt = SignedJWT.parse(token);
    assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("participant");
  }

  @Test
  void invalidInviteReturnsStableErrorCode() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-unknown\"}"))
        .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVITE_NOT_FOUND.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-invalid"));
  }

  @Test
  void blankInviteTokenReturnsStableValidationErrorCode() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-blank")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_INVITE.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-blank"));
  }

        @Test
        void blankDisplayNameReturnsStableValidationErrorCode() throws Exception {
          mockMvc.perform(post("/api/v1/invites/exchange")
          .with(csrf())
          .header("X-Trace-Id", "trace-display-blank")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{" + "\"inviteToken\":\"invite-valid\",\"displayName\":\"   \"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_INVITE.code()))
          .andExpect(jsonPath("$.properties.requestId").value("trace-display-blank"));
        }

        @Test
        void controlCharactersInDisplayNameReturnStableValidationErrorCode() throws Exception {
          mockMvc.perform(post("/api/v1/invites/exchange")
          .with(csrf())
          .header("X-Trace-Id", "trace-display-control")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{" + "\"inviteToken\":\"invite-valid\",\"displayName\":\"Guest\\nUser\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_INVITE.code()))
          .andExpect(jsonPath("$.properties.requestId").value("trace-display-control"));
        }

  private String extractToken(String joinUrl) {
    URI uri = URI.create(joinUrl);
    String fragment = uri.getFragment();
    if (fragment == null) {
      throw new IllegalStateException("JWT token is missing in joinUrl fragment");
    }

    for (String pair : fragment.split("&")) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && "jwt".equals(parts[0])) {
        String decoded = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
        if (decoded.length() >= 2 && decoded.startsWith("\"") && decoded.endsWith("\"")) {
          return decoded.substring(1, decoded.length() - 1);
        }
        return decoded;
      }
    }

    throw new IllegalStateException("JWT token is missing in joinUrl fragment");
  }
}

