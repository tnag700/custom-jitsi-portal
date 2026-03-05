package com.acme.jitsi.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.acme.jitsi.shared.JwtTestProperties;
import org.springframework.http.MediaType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "app.security.sso.expected-issuer=https://issuer.example.test",
      JwtTestProperties.TOKEN_ISSUER,
      JwtTestProperties.TOKEN_AUDIENCE,
      JwtTestProperties.TOKEN_ROLE_CLAIM_NAME,
      JwtTestProperties.TOKEN_ALGORITHM,
      JwtTestProperties.TOKEN_TTL_MINUTES,
      JwtTestProperties.TOKEN_SIGNING_SECRET,
      "app.auth.refresh.idle-ttl-minutes=60",
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES
    })
@AutoConfigureMockMvc
class AuthControllerSecurityTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void loginEndpointRedirectsToOidcAuthorizationEndpoint() throws Exception {
    mockMvc.perform(get("/api/v1/auth/login"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "/oauth2/authorization/keycloak"));
  }

  @Test
  void authErrorEndpointReturnsStableAccessDeniedPayload() throws Exception {
    mockMvc.perform(get("/api/v1/auth/error").param("code", "ACCESS_DENIED"))
      .andExpect(status().isForbidden())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.type").value("about:blank"))
      .andExpect(jsonPath("$.status").value(403))
      .andExpect(jsonPath("$.properties.errorCode").value("ACCESS_DENIED"))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.title").exists())
      .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void authErrorEndpointFallsBackToAuthRequiredForUnknownCode() throws Exception {
    mockMvc.perform(get("/api/v1/auth/error").param("code", "UNEXPECTED"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(401))
      .andExpect(jsonPath("$.instance").value("/api/v1/auth/error"))
      .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty())
        .andExpect(jsonPath("$.title").exists())
      .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void authErrorEndpointUsesDefaultAuthRequiredCodeWhenParameterMissing() throws Exception {
    mockMvc.perform(get("/api/v1/auth/error"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.status").value(401))
      .andExpect(jsonPath("$.instance").value("/api/v1/auth/error"))
      .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
  }

  @Test
  void csrfEndpointReturnsTokenAndHeaderName() throws Exception {
    mockMvc.perform(get("/api/v1/auth/csrf"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andExpect(jsonPath("$.headerName").isNotEmpty());
  }

  @Test
  void authenticatedRequestToAuthMeReturnsSafeProfile() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")
            .with(oauth2Login().attributes(attrs -> {
              attrs.put("sub", "u-1");
              attrs.put("name", "Ivan Ivanov");
              attrs.put("email", "ivan@example.com");
              attrs.put("tenant", "acme");
            })))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("u-1"))
        .andExpect(jsonPath("$.displayName").value("Ivan Ivanov"))
        .andExpect(jsonPath("$.email").value("ivan@example.com"))
        .andExpect(jsonPath("$.tenant").value("acme"))
        .andExpect(jsonPath("$.claims").isArray());
  }

  @Test
    void loginEndpointWithUnsupportedApiVersionRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v2/auth/login"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v2/auth/login"))
      .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"));
  }

  @Test
    void loginEndpointWithoutApiVersionSegmentRequiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/auth/login"))
      .andExpect(status().isUnauthorized())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/auth/login"))
      .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"));
  }
}