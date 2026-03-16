package com.acme.jitsi.domains.meetings.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.meetings.event.MeetingParticipantAssignedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRemovedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingParticipantRoleChangedEvent;
import com.acme.jitsi.support.TestDomainModuleApplication;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.JwtTestProperties;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(
  classes = TestDomainModuleApplication.class,
  properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb-participants;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.main.allow-bean-definition-overriding=true",
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
    })
@AutoConfigureMockMvc
@RecordApplicationEvents
class MeetingParticipantAssignmentsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private ApplicationEvents applicationEvents;

  private String roomId;
  private String meetingId;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.execute("DELETE FROM meeting_participant_assignments");
    jdbcTemplate.execute("DELETE FROM meeting_audit_events");
    jdbcTemplate.execute("DELETE FROM meetings");
    jdbcTemplate.execute("DELETE FROM rooms");
    jdbcTemplate.execute("DELETE FROM user_profiles");

    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-p1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Participants test room",
                  "tenantId": "tenant-p1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    roomId = JsonPath.parse(roomResponse).read("$.roomId");

    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-p1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Participants meeting",
                  "meetingType": "scheduled",
                  "startsAt": "2026-06-01T10:00:00Z",
                  "endsAt": "2026-06-01T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");
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
  void assignParticipant_returnsCreatedWithAssignment() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-assign-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-host-1", "role": "host" }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.subjectId").value("user-host-1"))
        .andExpect(jsonPath("$.role").value("host"))
        .andExpect(jsonPath("$.meetingId").value(meetingId))
        .andExpect(jsonPath("$.assignedBy").value("admin-user"));

      var assignedEvents = applicationEvents.stream(MeetingParticipantAssignedEvent.class)
        .filter(e -> e.meetingId().equals(meetingId)
          && e.roomId().equals(roomId)
          && e.actorId().equals("admin-user")
          && !e.traceId().isBlank()
          && e.subjectId().equals("user-host-1")
          && e.changedFields().contains("subjectId:user-host-1")
          && e.changedFields().contains("role:none->host"))
        .toList();
      assertEquals(1, assignedEvents.size());

      Integer auditCount = awaitAuditCount(assignedEvents.getFirst().traceId(), "assign");
      assertEquals(1, auditCount);
  }

  @Test
  void assignParticipant_invalidRole_returnsProblemDetail() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-invalid-role-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-x", "role": "superadmin" }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_ROLE.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-invalid-role-1"));
  }

  @Test
  void listParticipants_returnsAllAssignments() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-list-1", "role": "participant" }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(adminUser("admin-user", "tenant-p1")))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].subjectId").value("user-list-1"));
  }

  @Test
  void updateParticipant_changesRole() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-update-1", "role": "participant" }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(put("/api/v1/meetings/{meetingId}/participants/{subjectId}", meetingId, "user-update-1")
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-update-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
        { "role": "moderator" }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("moderator"));

      var updatedEvents = applicationEvents.stream(MeetingParticipantRoleChangedEvent.class)
        .filter(e -> e.meetingId().equals(meetingId)
          && e.roomId().equals(roomId)
          && e.actorId().equals("admin-user")
          && !e.traceId().isBlank()
          && e.subjectId().equals("user-update-1")
          && e.changedFields().contains("subjectId:user-update-1")
          && e.changedFields().contains("role:participant->moderator"))
        .toList();
      assertEquals(1, updatedEvents.size());

      Integer auditCount = awaitAuditCount(updatedEvents.getFirst().traceId(), "update");
      assertEquals(1, auditCount);
  }

  @Test
  void updateParticipant_notFound_returnsProblemDetail() throws Exception {
    mockMvc.perform(put("/api/v1/meetings/{meetingId}/participants/{subjectId}", meetingId, "no-such-user")
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-not-found-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
        { "role": "moderator" }
                """))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ASSIGNMENT_NOT_FOUND.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-not-found-1"));
  }

  @Test
  void unassignParticipant_returnsNoContent() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-delete-1", "role": "participant" }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(delete("/api/v1/meetings/{meetingId}/participants/{subjectId}", meetingId, "user-delete-1")
            .with(csrf())
        .with(adminUser("admin-user", "tenant-p1"))
        .header("X-Trace-Id", "trace-unassign-1"))
        .andExpect(status().isNoContent());

    long eventCount = applicationEvents.stream(MeetingParticipantRemovedEvent.class)
        .filter(e -> e.meetingId().equals(meetingId)
            && e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
        && !e.traceId().isBlank()
            && e.subjectId().equals("user-delete-1")
            && e.changedFields().equals("subjectId:user-delete-1;role:participant->none"))
        .count();
    assertEquals(1, eventCount);

        Integer auditCount = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM meeting_audit_events WHERE action_type = ?",
          Integer.class,
          "unassign");
        assertEquals(1, auditCount);
  }

  @Test
  void bulkAssignParticipants_returnsCreatedWithAll() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants/bulk", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-bulk-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "participants": [
                    { "subjectId": "bulk-user-1" },
                    { "subjectId": "bulk-user-2", "role": "moderator" }
                  ],
                  "defaultRole": "participant"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].subjectId").value("bulk-user-1"))
        .andExpect(jsonPath("$[0].role").value("participant"))
        .andExpect(jsonPath("$[0].meetingId").value(meetingId))
        .andExpect(jsonPath("$[1].subjectId").value("bulk-user-2"))
        .andExpect(jsonPath("$[1].role").value("moderator"))
        .andExpect(jsonPath("$[1].meetingId").value(meetingId));

      long eventCount = applicationEvents.stream(MeetingParticipantAssignedEvent.class)
          .filter(e -> e.meetingId().equals(meetingId)
              && e.roomId().equals(roomId)
              && e.actorId().equals("admin-user")
            && !e.traceId().isBlank()
              && (e.subjectId().equals("bulk-user-1") || e.subjectId().equals("bulk-user-2")))
          .count();
      assertEquals(2, eventCount);
  }

  @Test
  void bulkAssignParticipants_roleConflict_rollsBackEntireTransaction() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "existing-host", "role": "host" }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants/bulk", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-bulk-conflict")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "participants": [
                    { "subjectId": "bulk-participant-1", "role": "participant" },
                    { "subjectId": "second-host", "role": "host" }
                  ]
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_ROLE_CONFLICT.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-bulk-conflict"));

    mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(adminUser("admin-user", "tenant-p1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].subjectId").value("existing-host"));

    long eventCount = applicationEvents.stream(MeetingParticipantAssignedEvent.class)
        .filter(e -> e.meetingId().equals(meetingId)
            && e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
            && (e.subjectId().equals("bulk-participant-1") || e.subjectId().equals("second-host")))
        .count();
    assertEquals(0, eventCount);

        Integer auditCount = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM meeting_audit_events WHERE action_type = ?",
          Integer.class,
          "assign");
        assertEquals(1, auditCount);
  }

  @Test
  void bulkAssignParticipants_partialFailure_invalidRole_rollsBackTransaction() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants/bulk", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-bulk-partial-failure")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "participants": [
                    { "subjectId": "bulk-ok-1", "role": "participant" },
                    { "subjectId": "bulk-bad-1", "role": "superadmin" }
                  ]
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-bulk-partial-failure"));

    mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(adminUser("admin-user", "tenant-p1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    long eventCount = applicationEvents.stream(MeetingParticipantAssignedEvent.class)
        .filter(e -> e.meetingId().equals(meetingId)
            && e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
            && (e.subjectId().equals("bulk-ok-1") || e.subjectId().equals("bulk-bad-1")))
        .count();
    assertEquals(0, eventCount);

        Integer auditCount = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM meeting_audit_events WHERE action_type = ?",
          Integer.class,
          "assign");
        assertEquals(0, auditCount);
  }

  @Test
  void bulkAssignParticipants_duplicateSubjectId_returnsBadRequestAndRollsBackTransaction() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants/bulk", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .header("X-Trace-Id", "trace-bulk-duplicate-subject")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "participants": [
                    { "subjectId": "dup-user-1", "role": "participant" },
                    { "subjectId": "dup-user-1", "role": "moderator" }
                  ]
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_REQUEST.code()))
        .andExpect(jsonPath("$.properties.subjectId").value("dup-user-1"))
        .andExpect(jsonPath("$.properties.requestId").value("trace-bulk-duplicate-subject"));

    mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(adminUser("admin-user", "tenant-p1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    long eventCount = applicationEvents.stream(MeetingParticipantAssignedEvent.class)
        .filter(e -> e.meetingId().equals(meetingId)
            && e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
            && e.subjectId().equals("dup-user-1"))
        .count();
    assertEquals(0, eventCount);

    Integer auditCount = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM meeting_audit_events WHERE action_type = ?",
        Integer.class,
      "assign");
    assertEquals(0, auditCount);
  }

  @Test
  void assignParticipant_wrongTenant_returnsForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "other-user");
                  attrs.put("tenantId", "tenant-other");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-x", "role": "participant" }
                """))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TENANT_ACCESS_DENIED.code()));
  }

  @Test
  void listParticipants_enrichesWithProfileData() throws Exception {
    // Create a profile for user-enriched-1
    mockMvc.perform(put("/api/v1/profile/me")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "user-enriched-1");
                  attrs.put("tenantId", "tenant-p1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "fullName": "Иванов Иван", "organization": "ACME Corp", "position": "Разработчик" }
                """))
        .andExpect(status().isOk());

    // Assign participant with profile
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-enriched-1", "role": "participant" }
                """))
        .andExpect(status().isCreated());

    // Assign participant without profile
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(csrf())
            .with(adminUser("admin-user", "tenant-p1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                { "subjectId": "user-no-profile", "role": "moderator" }
                """))
        .andExpect(status().isCreated());

    // List participants — enriched user has profile fields, other has nulls
    String response = mockMvc.perform(get("/api/v1/meetings/{meetingId}/participants", meetingId)
            .with(adminUser("admin-user", "tenant-p1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andReturn().getResponse().getContentAsString();

    // Find indices by subjectId since order may vary
    net.minidev.json.JSONArray arr = JsonPath.parse(response).read("$[*].subjectId");
    int enrichedIdx = arr.indexOf("user-enriched-1");
    int noProfileIdx = arr.indexOf("user-no-profile");

    assertEquals("Иванов Иван", JsonPath.parse(response).read("$[" + enrichedIdx + "].fullName"));
    assertEquals("ACME Corp", JsonPath.parse(response).read("$[" + enrichedIdx + "].organization"));
    assertEquals("Разработчик", JsonPath.parse(response).read("$[" + enrichedIdx + "].position"));
    assertNull(JsonPath.parse(response).read("$[" + noProfileIdx + "].fullName"));
    assertNull(JsonPath.parse(response).read("$[" + noProfileIdx + "].organization"));
    assertNull(JsonPath.parse(response).read("$[" + noProfileIdx + "].position"));
  }

  private Integer awaitAuditCount(String traceId, String actionType) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      Integer auditCount = jdbcTemplate.queryForObject(
          "SELECT COUNT(*) FROM meeting_audit_events WHERE trace_id = ? AND action_type = ?",
          Integer.class,
          traceId,
          actionType);
      if (auditCount != null && auditCount > 0) {
        return auditCount;
      }
      Thread.sleep(25);
    }

    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM meeting_audit_events WHERE trace_id = ? AND action_type = ?",
        Integer.class,
        traceId,
        actionType);
  }

}
