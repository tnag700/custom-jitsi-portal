package com.acme.jitsi.domains.meetings.api;

import static com.acme.jitsi.shared.TestFixtures.adminLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.JwtTestProperties;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
  "spring.datasource.url=jdbc:h2:mem:testdb-meeting-invites-bulk;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
      "app.rooms.valid-config-sets=config-1,config-2",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role",
      "app.rooms.config-sets.config-2.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-2.audience=jitsi-meet",
      "app.rooms.config-sets.config-2.role-claim=role",
      "app.invites.mode=database",
    })
@AutoConfigureMockMvc
class MeetingInvitesControllerBulkTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private String roomId;
  private String meetingId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.execute("DELETE FROM meeting_audit_events");
    jdbcTemplate.execute("DELETE FROM meeting_invites");
    jdbcTemplate.execute("DELETE FROM meeting_participant_assignments");
    jdbcTemplate.execute("DELETE FROM meetings");
    jdbcTemplate.execute("DELETE FROM rooms");

    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Bulk Invite Room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    roomId = JsonPath.parse(roomResponse).read("$.roomId");

    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-bulk-meeting-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Bulk Invite Meeting",
                  "description": "Meeting for bulk invite tests",
                  "meetingType": "scheduled",
                  "startsAt": "2099-02-17T10:00:00Z",
                  "endsAt": "2099-02-17T11:00:00Z",
                  "allowGuests": true
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");
  }

  @Test
  void bulkCreateInvites_successReturnsCreated() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites/bulk", meetingId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-bulk-success")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "recipients": [
                    { "email": "user1@example.com", "role": "participant" },
                    { "email": "user2@example.com", "role": "moderator" }
                  ],
                  "defaultTtlMinutes": 120,
                  "defaultMaxUses": 1,
                  "defaultRole": "participant",
                  "duplicatePolicy": "SKIP_EXISTING"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.summary.total").value(2))
        .andExpect(jsonPath("$.summary.created").value(2))
        .andExpect(jsonPath("$.summary.failed").value(0));
  }

  @Test
  void bulkCreateInvites_partialFailureReturnsProblemDetails422() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites/bulk", meetingId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-bulk-partial")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "recipients": [
                    { "email": "valid@example.com", "role": "participant" },
                    { "email": "broken-email", "role": "participant", "rowIndex": 2 }
                  ],
                  "defaultTtlMinutes": 120,
                  "defaultMaxUses": 1,
                  "defaultRole": "participant",
                  "duplicatePolicy": "SKIP_EXISTING"
                }
                """))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.BULK_INVITE_VALIDATION_FAILED.code()))
        .andExpect(jsonPath("$.properties.summary.total").value(2))
        .andExpect(jsonPath("$.properties.summary.created").value(1))
        .andExpect(jsonPath("$.properties.summary.failed").value(1))
        .andExpect(jsonPath("$.properties.errors[0].rowIndex").value(2));
  }

  @Test
  void bulkCreateInvites_e2eForFiftyPlusRecipients_returnsCreatedSummary() throws Exception {
    int recipientsCount = 55;
    StringBuilder recipientsJson = new StringBuilder();
    recipientsJson.append("[");
    for (int index = 1; index <= recipientsCount; index++) {
      if (index > 1) {
        recipientsJson.append(",");
      }
      recipientsJson.append("{\"email\":\"loaduser")
          .append(index)
          .append("@example.com\",\"role\":\"participant\",\"rowIndex\":")
          .append(index)
          .append("}");
    }
    recipientsJson.append("]");

    String requestBody = "{" +
        "\"recipients\":" + recipientsJson + "," +
        "\"defaultTtlMinutes\":120," +
        "\"defaultMaxUses\":1," +
        "\"defaultRole\":\"participant\"," +
        "\"duplicatePolicy\":\"SKIP_EXISTING\"" +
        "}";

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites/bulk", meetingId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-bulk-e2e-50")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestBody))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.summary.total").value(recipientsCount))
        .andExpect(jsonPath("$.summary.created").value(recipientsCount))
        .andExpect(jsonPath("$.summary.skipped").value(0))
        .andExpect(jsonPath("$.summary.failed").value(0));

      Map<String, Object> bulkAudit = awaitLatestBulkAudit(meetingId);

      org.assertj.core.api.Assertions.assertThat(bulkAudit.get("action_type")).isEqualTo("bulk_invite_create");
      org.assertj.core.api.Assertions.assertThat(bulkAudit.get("room_id")).isEqualTo(roomId);
      org.assertj.core.api.Assertions.assertThat(bulkAudit.get("meeting_id")).isEqualTo(meetingId);
      org.assertj.core.api.Assertions.assertThat(bulkAudit.get("actor_id")).isEqualTo("admin-user");
      org.assertj.core.api.Assertions.assertThat(String.valueOf(bulkAudit.get("trace_id"))).isNotBlank();
      org.assertj.core.api.Assertions.assertThat(String.valueOf(bulkAudit.get("changed_fields")))
        .contains("total=55")
        .contains("success=55")
        .contains("failed=0")
        .contains("skipped=0");
  }

  @Test
  void bulkCreateInvites_invalidDefaultsReturnsValidationErrorCode() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites/bulk", meetingId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-bulk-invalid-defaults")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "recipients": [
                    { "email": "user1@example.com", "role": "participant" }
                  ],
                  "defaultTtlMinutes": 0,
                  "defaultMaxUses": 0,
                  "defaultRole": "participant",
                  "duplicatePolicy": "SKIP_EXISTING"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.VALIDATION_ERROR.code()));
  }

  private Map<String, Object> awaitLatestBulkAudit(String meetingId) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      List<Map<String, Object>> rows = jdbcTemplate.queryForList(
          """
            select action_type, room_id, meeting_id, actor_id, trace_id, changed_fields
            from meeting_audit_events
            where meeting_id = ? and action_type = 'bulk_invite_create'
            order by created_at desc
            limit 1
            """,
          meetingId);
      if (!rows.isEmpty()) {
        return rows.getFirst();
      }
      Thread.sleep(25);
    }

    throw new AssertionError("Timed out waiting for bulk invite audit row");
  }
}
