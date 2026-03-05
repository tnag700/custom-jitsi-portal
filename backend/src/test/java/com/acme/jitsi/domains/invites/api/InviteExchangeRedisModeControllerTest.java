package com.acme.jitsi.domains.invites.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
      "app.invites.exchange.atomic-store=redis",
      "app.invites.exchange.invites[0].token=invite-redis",
      "app.invites.exchange.invites[0].meeting-id=meeting-a",
      "app.invites.exchange.invites[0].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[0].usage-limit=1",
      "app.invites.exchange.known-meeting-ids=meeting-a"
    })
@AutoConfigureMockMvc
class InviteExchangeRedisModeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void redisAtomicModeWithoutRedisReturnsConfigIncompatibleViaProblemDetail() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-config-incompatible")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"inviteToken\":\"invite-redis\"}"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("CONFIG_INCOMPATIBLE"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-config-incompatible"));
  }
}
