package com.acme.jitsi.domains.invites.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
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
      "app.invites.mode=database",
      "app.rooms.valid-config-sets=config-1",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role"
    })
@AutoConfigureMockMvc
class InviteExchangeDatabaseModeControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void oneInviteLinkWithUsageLimitThree_allowsThreeGuests_thenExhausted() throws Exception {
    String roomId = createRoom("Guests Room", "tenant-1");
    String meetingId = createMeeting(roomId, "tenant-1");
    String inviteToken = createInvite(meetingId, "tenant-1", 3);

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteToken": "%s",
                  "displayName": "Guest One"
                }
                """.formatted(inviteToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("participant"))
        .andExpect(jsonPath("$.meetingId").value(meetingId));

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteToken": "%s",
                  "displayName": "Guest Two"
                }
                """.formatted(inviteToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("participant"))
        .andExpect(jsonPath("$.meetingId").value(meetingId));

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteToken": "%s",
                  "displayName": "Guest Three"
                }
                """.formatted(inviteToken)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("participant"))
        .andExpect(jsonPath("$.meetingId").value(meetingId));

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .header("X-Trace-Id", "trace-invite-exhausted-3")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "inviteToken": "%s",
                  "displayName": "Guest Four"
                }
                """.formatted(inviteToken)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.properties.errorCode").value("INVITE_EXHAUSTED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-invite-exhausted-3"));
  }

  private OAuth2LoginRequestPostProcessor adminLogin(String tenantId) {
    return oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", tenantId);
          attrs.put("tenant_id", tenantId);
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));
  }

  private String createRoom(String name, String tenantId) throws Exception {
    String response = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(adminLogin(tenantId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "%s",
                  "tenantId": "%s",
                  "configSetId": "config-1"
                }
                """.formatted(name, tenantId)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    return JsonPath.parse(response).read("$.roomId");
  }

  private String createMeeting(String roomId, String tenantId) throws Exception {
    String response = mockMvc.perform(post("/api/v1/rooms/" + roomId + "/meetings")
            .with(csrf())
            .with(adminLogin(tenantId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Guests Meeting",
                  "description": "Meeting for guests",
                  "meetingType": "scheduled",
                  "startsAt": "2099-01-01T10:00:00Z",
                  "endsAt": "2099-01-01T11:00:00Z",
                  "allowGuests": true,
                  "recordingEnabled": false
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    return JsonPath.parse(response).read("$.meetingId");
  }

  private String createInvite(String meetingId, String tenantId, int maxUses) throws Exception {
    String response = mockMvc.perform(post("/api/v1/meetings/" + meetingId + "/invites")
            .with(csrf())
            .with(adminLogin(tenantId))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "PARTICIPANT",
                  "maxUses": %d,
                  "expiresInHours": 24
                }
                """.formatted(maxUses)))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    return JsonPath.parse(response).read("$.token");
  }
}
