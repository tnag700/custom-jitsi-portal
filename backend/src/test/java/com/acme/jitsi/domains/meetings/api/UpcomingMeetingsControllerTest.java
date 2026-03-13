package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.shared.ErrorCode;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import com.jayway.jsonpath.JsonPath;

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
      "app.auth.refresh.idle-ttl-minutes=60",
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES,
      "app.rooms.valid-config-sets=config-1",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role",
      "app.meetings.token.known-meeting-ids=meeting-a,meeting-b",
      "app.meetings.token.assignments[0].meeting-id=meeting-a",
      "app.meetings.token.assignments[0].subject=u-host",
      "app.meetings.token.assignments[0].role=host",
      "app.meetings.token.assignments[1].meeting-id=meeting-b",
      "app.meetings.token.assignments[1].subject=u-host",
      "app.meetings.token.assignments[1].role=participant",
      "app.meetings.token.upcoming-meetings[0].meeting-id=meeting-b",
      "app.meetings.token.upcoming-meetings[0].title=Daily standup",
      "app.meetings.token.upcoming-meetings[0].starts-at=2099-01-01T09:00:00Z",
      "app.meetings.token.upcoming-meetings[0].room-name=Room-B",
      "app.meetings.token.upcoming-meetings[1].meeting-id=meeting-a",
      "app.meetings.token.upcoming-meetings[1].title=Architecture sync",
      "app.meetings.token.upcoming-meetings[1].starts-at=2099-01-01T08:00:00Z",
      "app.meetings.token.upcoming-meetings[1].room-name=Room-A",
      "app.meetings.token.upcoming-meetings[2].meeting-id=meeting-past",
      "app.meetings.token.upcoming-meetings[2].title=Past meeting",
      "app.meetings.token.upcoming-meetings[2].starts-at=2000-01-01T08:00:00Z",
      "app.meetings.token.upcoming-meetings[2].room-name=Room-P"
    })
@AutoConfigureMockMvc
@Tag("integration")
class UpcomingMeetingsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanTables() {
    jdbcTemplate.execute("DELETE FROM meeting_participant_assignments");
    jdbcTemplate.execute("DELETE FROM meeting_invites");
    jdbcTemplate.execute("DELETE FROM meeting_audit_events");
    jdbcTemplate.execute("DELETE FROM meetings");
    jdbcTemplate.execute("DELETE FROM rooms");
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor adminUser(String sub, String tenantId) {
    return oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", sub);
          attrs.put("tenantId", tenantId);
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));
  }

  @Test
  void unauthenticatedRequestReturns401WithStableProblemContract() throws Exception {
    mockMvc.perform(get("/api/v1/meetings/upcoming")
            .with(csrf())
            .header("X-Trace-Id", "trace-upcoming-auth-1"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value("/api/v1/meetings/upcoming"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-upcoming-auth-1"));
  }

  @Test
  void authenticatedRequestReturnsAssignedMeetingFromDatabase() throws Exception {
    String roomResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/rooms")
            .with(csrf())
            .with(adminUser("admin-upcoming", "tenant-upcoming"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Upcoming test room",
                  "tenantId": "tenant-upcoming",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    String meetingResponse = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(adminUser("admin-upcoming", "tenant-upcoming"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Assigned DB meeting",
                  "meetingType": "scheduled",
                  "startsAt": "2099-01-01T10:00:00Z",
                  "endsAt": "2099-01-01T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");

    mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-upcoming", "tenant-upcoming"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-upcoming", "role": "participant" }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/meetings/upcoming")
            .with(csrf())
            .with(oauth2Login().attributes(attrs -> {
              attrs.put("sub", "user-upcoming");
              attrs.put("tenantId", "tenant-upcoming");
            })))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].meetingId").value(meetingId))
        .andExpect(jsonPath("$[0].title").value("Assigned DB meeting"));
  }
}


