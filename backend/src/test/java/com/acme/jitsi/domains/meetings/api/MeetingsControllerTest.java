package com.acme.jitsi.domains.meetings.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.meetings.event.MeetingCanceledEvent;
import com.acme.jitsi.domains.meetings.event.MeetingCreatedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingUpdatedEvent;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.JwtTestProperties;
import com.acme.jitsi.shared.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import org.junit.jupiter.api.Tag;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
    properties = {
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
    })
@AutoConfigureMockMvc
@RecordApplicationEvents
@Tag("integration")
@Tag("container")
class MeetingsControllerTest extends RedisBackedMeetingApiIntegrationTestSupport {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ApplicationEvents applicationEvents;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  void createMeetingWithValidScheduleReturnsCreated() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(TestFixtures.adminLogin("tenant-1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(TestFixtures.adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-meeting-create-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Planning",
                  "description": "Sprint planning",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z",
                  "allowGuests": true,
                  "recordingEnabled": false
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.meetingId").isString())
        .andExpect(jsonPath("$.roomId").value(roomId))
        .andExpect(jsonPath("$.status").value("scheduled"))
        .andExpect(jsonPath("$.configSetId").value("config-1"))
        .andReturn().getResponse().getContentAsString();

      String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");

        var createdEvents = applicationEvents.stream(MeetingCreatedEvent.class)
          .filter(e -> e.meetingId().equals(meetingId)
            && e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
            && e.changedFields().equals("title,description,meetingType,startsAt,endsAt,allowGuests,recordingEnabled"))
          .toList();
        assertEquals(1, createdEvents.size());

          Integer auditCount = awaitAuditCount(createdEvents.getFirst().traceId(), "create");
            assertEquals(1, auditCount);
  }

  @Test
  void updateMeetingWritesAuditPayloadForAc6() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room update audit",
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
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Update target",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");

    mockMvc.perform(put("/api/v1/meetings/{meetingId}", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-meeting-update-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Update target renamed"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.meetingId").value(meetingId))
        .andExpect(jsonPath("$.title").value("Update target renamed"));

    var updatedEvents = applicationEvents.stream(MeetingUpdatedEvent.class)
      .filter(e -> e.meetingId().equals(meetingId)
        && e.roomId().equals(roomId)
        && e.actorId().equals("admin-user")
        && e.changedFields().equals("title"))
      .toList();
    assertEquals(1, updatedEvents.size());

      Integer auditCount = awaitAuditCount(updatedEvents.getFirst().traceId(), "update");
        assertEquals(1, auditCount);
  }

  @Test
  void createMeetingWithInvalidScheduleReturnsProblemDetail() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room 2",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-meeting-schedule-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Broken schedule",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T11:00:00Z",
                  "endsAt": "2026-02-17T10:00:00Z"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVALID_SCHEDULE.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-meeting-schedule-1"));
  }

  @Test
  void createMeetingWithDuplicateIdempotencyKeyReturnsConflict() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room idempotency",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");
    String idempotencyKey = "meeting-idempotency-key-1";

    mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Idempotent meeting",
                  "description": "Create once",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Idempotent meeting",
                  "description": "Create once",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.IDEMPOTENCY_CONFLICT.code()));
  }

  @Test
  void createMeetingWithConcurrentDuplicateIdempotencyKeyAllowsSingleCreate() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room idempotency concurrent",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");
    String idempotencyKey = "meeting-idempotency-concurrent-key";
    int threadCount = 8;

    CountDownLatch ready = new CountDownLatch(threadCount);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger createdCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);
    AtomicInteger unexpectedStatusCount = new AtomicInteger(0);

    try (ExecutorService executorService = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        executorService.submit(() -> {
          ready.countDown();
          try {
            start.await();
            int responseStatus = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
                    .with(csrf())
                    .with(oauth2Login()
                        .attributes(attrs -> {
                          attrs.put("sub", "admin-user");
                          attrs.put("tenantId", "tenant-1");
                        })
                        .authorities(new SimpleGrantedAuthority("ROLE_admin")))
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "title": "Idempotent concurrent meeting",
                          "description": "Create once in parallel",
                          "meetingType": "scheduled",
                          "startsAt": "2026-02-17T10:00:00Z",
                          "endsAt": "2026-02-17T11:00:00Z"
                        }
                        """))
                .andReturn().getResponse().getStatus();

            if (responseStatus == 201) {
              createdCount.incrementAndGet();
            } else if (responseStatus == 409) {
              conflictCount.incrementAndGet();
            } else {
              unexpectedStatusCount.incrementAndGet();
            }
          } catch (Exception exception) {
            unexpectedStatusCount.incrementAndGet();
          }
        });
      }

      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      executorService.shutdown();
      assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));

      assertEquals(1, createdCount.get());
      assertEquals(threadCount - 1, conflictCount.get());
      assertEquals(0, unexpectedStatusCount.get());
    }
  }

  @Test
  void cancelThenUpdateReturnsMeetingFinalized() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room 3",
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
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Cancelable",
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
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-meeting-cancel-1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("canceled"));

    mockMvc.perform(put("/api/v1/meetings/{meetingId}", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-meeting-finalized-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Updated title"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.MEETING_FINALIZED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-meeting-finalized-1"));

        var canceledEvents = applicationEvents.stream(MeetingCanceledEvent.class)
          .filter(e -> e.meetingId().equals(meetingId)
            && e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
            && e.changedFields().equals("status"))
          .toList();
        assertEquals(1, canceledEvents.size());

          Integer auditCount = awaitAuditCount(canceledEvents.getFirst().traceId(), "cancel");
            assertEquals(1, auditCount);
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

  @Test
  void listMeetingsReturnsPagedResponse() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room 4",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    mockMvc.perform(get("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").isNumber())
        .andExpect(jsonPath("$.pageSize").isNumber())
        .andExpect(jsonPath("$.totalElements").isNumber());
  }

  @Test
  void createMeetingWithoutTenantClaimReturnsForbidden() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room 5",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-meeting-tenant-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Planning",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TENANT_CLAIM_REQUIRED.code()))
        .andExpect(jsonPath("$.properties.requestId").value("trace-meeting-tenant-1"));
  }

  @Test
  void listMeetingsWithSizeZeroFallsBackToDefaultPageSize() throws Exception {
    String roomResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Meetings room size-zero",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String roomId = JsonPath.parse(roomResponse).read("$.roomId");

    mockMvc.perform(get("/api/v1/rooms/{roomId}/meetings?size=0", roomId)
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.pageSize").value(20));
  }

  @Test
  void listMeetingsForMissingRoomReturnsRoomNotFoundProblemDetail() throws Exception {
    mockMvc.perform(get("/api/v1/rooms/{roomId}/meetings", "missing-room")
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ROOM_NOT_FOUND.code()));
  }
}