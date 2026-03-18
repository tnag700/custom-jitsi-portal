package com.acme.jitsi.domains.auth.api;

import com.acme.jitsi.shared.ErrorCode;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.security.TokenIssuancePolicyException;
import com.acme.jitsi.shared.JwtTestProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
      "app.meetings.token.known-meeting-ids=meeting-a",
      "app.meetings.token.assignments[0].meeting-id=meeting-a",
      "app.meetings.token.assignments[0].subject=u-host",
      "app.meetings.token.assignments[0].role=host",
      "app.meetings.token.assignments[1].meeting-id=meeting-a",
      "app.meetings.token.assignments[1].subject=u-mod",
      "app.meetings.token.assignments[1].role=moderator",
      "app.auth.refresh.atomic-store=in-memory",
      "app.auth.refresh.idle-ttl-minutes=60",
      "app.auth.refresh.revoked-token-ids=revoked-jti-1"
    })
@AutoConfigureMockMvc
class AuthRefreshControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy;

  @Test
  void successfulRefreshReturnsNewPairAndInvalidatesOldToken() throws Exception {
    String refreshToken = buildRefreshToken("u-host", "meeting-a", "refresh-jti-1", Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS));

    MvcResult first = mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"))
        .andExpect(jsonPath("$.role").value("host"))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty())
        .andReturn();

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .header("X-Trace-Id", "trace-reuse-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.REFRESH_REUSE_DETECTED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.properties.requestId").value("trace-reuse-1"));

    String newRefreshToken = com.jayway.jsonpath.JsonPath.parse(first.getResponse().getContentAsString())
        .read("$.refreshToken", String.class);

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + newRefreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
  }

  @Test
  void joinPathRevokedRefreshTokenReturnsFacadeProblemDetail() throws Exception {
    String refreshToken = buildRefreshToken("u-host", "meeting-a", "revoked-jti-1", Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .header("X-Trace-Id", "trace-revoked-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.instance").value("/api/v1/auth/refresh"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TOKEN_REVOKED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.properties.requestId").value("trace-revoked-1"));
  }

  @Test
  void joinPathIdleExpiredRefreshTokenReturnsFacadeProblemDetail() throws Exception {
    Instant issuedAt = Instant.now().minus(2, ChronoUnit.HOURS);
    Instant expiresAt = Instant.now().plus(2, ChronoUnit.HOURS);
    String refreshToken = buildRefreshToken("u-host", "meeting-a", "idle-expired-jti-1", issuedAt, expiresAt);

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .header("X-Trace-Id", "trace-idle-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.instance").value("/api/v1/auth/refresh"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.properties.requestId").value("trace-idle-1"));
  }

  @Test
          void joinPathAbsoluteExpiredRefreshTokenReturnsFacadeProblemDetail() throws Exception {
    Instant issuedAt = Instant.now().minus(4, ChronoUnit.HOURS);
    Instant expiresAt = Instant.now().minus(2, ChronoUnit.HOURS);
    String refreshToken = buildRefreshToken("u-host", "meeting-a", "absolute-expired-jti-1", issuedAt, expiresAt);

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .header("X-Trace-Id", "trace-absolute-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.instance").value("/api/v1/auth/refresh"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.properties.requestId").value("trace-absolute-1"));
  }

        @Test
        void joinPathInvalidRefreshTokenReturnsTokenInvalidProblemDetail() throws Exception {
          mockMvc.perform(post("/api/v1/auth/refresh")
          .with(csrf())
          .header("X-Trace-Id", "trace-invalid-refresh-1")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{" + "\"refreshToken\":\"not-a-jwt\"}"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.status").value(401))
          .andExpect(jsonPath("$.instance").value("/api/v1/auth/refresh"))
          .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TOKEN_INVALID.code()))
          .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
          .andExpect(jsonPath("$.properties.requestId").value("trace-invalid-refresh-1"));
        }

  @Test
  void roleIsRecomputedFromCurrentAssignmentsNotFromRefreshPayloadHints() throws Exception {
    String refreshToken = buildRefreshTokenWithOptionalRoleHint(
        "u-mod",
        "meeting-a",
        "role-recompute-jti-1",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS),
        "host");

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("moderator"));
  }

      @Test
      void configIncompatibleRefreshKeepsProblemContractAndDoesNotConsumeToken() throws Exception {
      String refreshToken = buildRefreshToken(
        "u-host",
        "meeting-a",
        "config-incompatible-jti-1",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

      doThrow(new TokenIssuancePolicyException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCode.CONFIG_INCOMPATIBLE.code(),
        "Token issuance is blocked due to incompatible active config set: cs-1"))
        .when(tokenIssuanceCompatibilityPolicy)
        .assertTokenIssuanceAllowed();

      mockMvc.perform(post("/api/v1/auth/refresh")
          .with(csrf())
          .header("X-Trace-Id", "trace-config-incompatible-1")
          .contentType(MediaType.APPLICATION_JSON)
          .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.CONFIG_INCOMPATIBLE.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.properties.requestId").value("trace-config-incompatible-1"));

      reset(tokenIssuanceCompatibilityPolicy);

      mockMvc.perform(post("/api/v1/auth/refresh")
          .with(csrf())
          .contentType(MediaType.APPLICATION_JSON)
          .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tokenType").value("Bearer"));
      }

  @Test
  void authenticatedRevokeEndpointBlocksFurtherRefreshByTokenId() throws Exception {
    String refreshToken = buildRefreshToken("u-host", "meeting-a", "runtime-revoke-jti-1", Instant.now(), Instant.now().plus(2, ChronoUnit.HOURS));

    mockMvc.perform(post("/api/v1/auth/refresh/revoke")
            .with(csrf())
            .with(oauth2Login())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"tokenId\":\"runtime-revoke-jti-1\"}"))
        .andExpect(status().isNoContent());

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .header("X-Trace-Id", "trace-runtime-revoke-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TOKEN_REVOKED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.properties.requestId").value("trace-runtime-revoke-1"));
  }

            @Test
            void revokeEndpointRejectsBlankTokenIdWithInvalidRequest() throws Exception {
          mockMvc.perform(post("/api/v1/auth/refresh/revoke")
              .with(csrf())
              .with(oauth2Login())
              .header("X-Trace-Id", "trace-revoke-bad-request-1")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{" + "\"tokenId\":\"\"}"))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
              .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
              .andExpect(jsonPath("$.properties.requestId").value("trace-revoke-bad-request-1"));
            }

            @Test
            void revokeEndpointRequiresAuthentication() throws Exception {
          mockMvc.perform(post("/api/v1/auth/refresh/revoke")
              .with(csrf())
              .header("X-Trace-Id", "trace-revoke-auth-required-1")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{" + "\"tokenId\":\"runtime-revoke-jti-2\"}"))
              .andExpect(status().isUnauthorized())
              .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()))
              .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
              .andExpect(jsonPath("$.properties.requestId").value("trace-revoke-auth-required-1"));
            }

            @Test
            void revokeEndpointRejectsRequestWithoutCsrf() throws Exception {
          mockMvc.perform(post("/api/v1/auth/refresh/revoke")
              .with(oauth2Login())
              .header("X-Trace-Id", "trace-revoke-access-denied-1")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{" + "\"tokenId\":\"runtime-revoke-jti-3\"}"))
              .andExpect(status().isForbidden())
              .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
              .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
              .andExpect(jsonPath("$.properties.requestId").value("trace-revoke-access-denied-1"));
            }

                @Test
                void refreshEndpointRejectsMalformedJsonWithInvalidRequest() throws Exception {
              mockMvc.perform(post("/api/v1/auth/refresh")
                  .with(csrf())
                  .header("X-Trace-Id", "trace-refresh-malformed-1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{" + "\"refreshToken\":\"abc\""))
                  .andExpect(status().isBadRequest())
                  .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
                  .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
                  .andExpect(jsonPath("$.properties.requestId").value("trace-refresh-malformed-1"));
                }

                @Test
                void revokeEndpointRejectsMalformedJsonWithInvalidRequest() throws Exception {
              mockMvc.perform(post("/api/v1/auth/refresh/revoke")
                  .with(csrf())
                  .with(oauth2Login())
                  .header("X-Trace-Id", "trace-revoke-malformed-1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{" + "\"tokenId\":\"abc\""))
                  .andExpect(status().isBadRequest())
                  .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
                  .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
                  .andExpect(jsonPath("$.properties.requestId").value("trace-revoke-malformed-1"));
                }

  private String buildRefreshToken(String subject, String meetingId, String jti, Instant issuedAt, Instant expiresAt)
      throws JOSEException {
      return buildRefreshTokenWithOptionalRoleHint(subject, meetingId, jti, issuedAt, expiresAt, null);
    }

    private String buildRefreshTokenWithOptionalRoleHint(
        String subject,
        String meetingId,
        String jti,
        Instant issuedAt,
        Instant expiresAt,
        String roleHint)
        throws JOSEException {
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("https://portal.example.test")
        .audience("jitsi-meet")
        .subject(subject)
        .issueTime(Date.from(issuedAt))
        .expirationTime(Date.from(expiresAt))
        .jwtID(jti)
        .claim("tokenType", "refresh")
        .claim("meetingId", meetingId)
        .build();

    JWTClaimsSet finalClaims = claims;
    if (roleHint != null && !roleHint.isBlank()) {
      finalClaims = new JWTClaimsSet.Builder(claims)
          .claim("role", roleHint)
          .build();
    }

    SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256)
        .type(new JOSEObjectType("JWT"))
        .build(), finalClaims);
    jwt.sign(new MACSigner("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}


