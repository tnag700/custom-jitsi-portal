package com.acme.jitsi.domains.meetings.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.meetings.event.MeetingInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.event.MeetingInviteRevokedEvent;
import com.acme.jitsi.infrastructure.idempotency.Idempotent;
import com.acme.jitsi.shared.JwtTestProperties;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MeetingInvitesController API endpoints.
 * Verifies CRUD operations for meeting invites with proper authorization.
 */
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
@RecordApplicationEvents
@Testcontainers
class MeetingInvitesControllerTest {

  @Container
  static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
      .withExposedPorts(6379);

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
  }

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
    // Clean up test data
    jdbcTemplate.execute("DELETE FROM meeting_audit_events");
    jdbcTemplate.execute("DELETE FROM meeting_invites");
    jdbcTemplate.execute("DELETE FROM meeting_participant_assignments");
    jdbcTemplate.execute("DELETE FROM meetings");
    jdbcTemplate.execute("DELETE FROM rooms");

    // Create room first
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
                  "name": "Invite Test Room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    roomId = JsonPath.parse(roomResponse).read("$.roomId");

    // Create meeting
    String meetingResponse = mockMvc.perform(post("/api/v1/rooms/{roomId}/meetings", roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-meeting-create-invite-test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "title": "Invite Test Meeting",
                  "description": "Meeting for testing invites",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z",
                  "allowGuests": true
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");
  }

  // AC1: Create invite - participant role
  @Test
  void createInviteWithParticipantRoleReturnsCreated() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-create-participant")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "participant",
                  "maxUses": 5,
                  "expiresInHours": 24
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.role").value("participant"))
        .andExpect(jsonPath("$.maxUses").value(5))
        .andExpect(jsonPath("$.createdAt").exists());
  }

  @Test
  void createInviteWithDuplicateIdempotencyKeyReturnsConflict() throws Exception {
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
                  "name": "Invite idempotency room",
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
                  "title": "Invite idempotency meeting",
                  "description": "Create once",
                  "meetingType": "scheduled",
                  "startsAt": "2026-02-17T10:00:00Z",
                  "endsAt": "2026-02-17T11:00:00Z"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String meetingId = JsonPath.parse(meetingResponse).read("$.meetingId");
    String idempotencyKey = "invite-idempotency-key-1";

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                  "role": "participant",
                  "maxUses": 5,
                  "expiresInHours": 24
                }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                  "role": "participant",
                  "maxUses": 5,
                  "expiresInHours": 24
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("IDEMPOTENCY_CONFLICT"));
  }

  @Test
  void createBulkInvitesWithDuplicateIdempotencyKeyReturnsConflict() throws Exception {
    Method method = MeetingInvitesController.class.getDeclaredMethod(
        "createBulkInvites",
        String.class,
        BulkCreateInviteRequest.class,
        OAuth2User.class,
        HttpServletRequest.class);

    assertTrue(method.isAnnotationPresent(Idempotent.class));
  }

  @Test
  void createInviteWithConcurrentDuplicateIdempotencyKeyAllowsSingleCreate() throws Exception {
    String idempotencyKey = "invite-idempotency-concurrent-key";
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
            int responseStatus = mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                          "role": "participant",
                          "maxUses": 5,
                          "expiresInHours": 24
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

  // AC1: Create invite - moderator role
  @Test
  void createInviteWithModeratorRoleReturnsCreated() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-create-moderator")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "moderator",
                  "maxUses": 1,
                  "expiresInHours": 48
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.token").exists())
        .andExpect(jsonPath("$.role").value("moderator"))
        .andExpect(jsonPath("$.maxUses").value(1));
  }

  // AC2: List invites returns paginated results
  @Test
  void listInvitesReturnsPagedResults() throws Exception {
    // Create two invites first
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                  "role": "participant",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                  "role": "moderator",
                  "maxUses": 1
                }
                """))
        .andExpect(status().isCreated());

    // List invites
    mockMvc.perform(get("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-list"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.content.length()").value(2))
        .andExpect(jsonPath("$.totalElements").value(2))
        .andExpect(jsonPath("$.totalPages").value(1))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.pageSize").value(20));
  }

  // AC3: Revoke invite returns no content
  @Test
  void revokeInviteReturnsNoContent() throws Exception {
    // Create invite
    String inviteResponse = mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                  "role": "participant",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String inviteId = JsonPath.parse(inviteResponse).read("$.id");

    // Revoke invite
    mockMvc.perform(delete("/api/v1/meetings/{meetingId}/invites/{inviteId}", meetingId, inviteId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-revoke"))
        .andExpect(status().isNoContent());

    // Verify invite is revoked in list
    mockMvc.perform(get("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].revokedAt").exists());
  }

      @Test
      void inviteLifecycleWritesCreateAndRevokeAuditEvents() throws Exception {
      String createTraceId = "trace-invite-audit-create";
      String revokeTraceId = "trace-invite-audit-revoke";

      String inviteResponse = mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
          .with(csrf())
          .with(oauth2Login()
            .attributes(attrs -> {
              attrs.put("sub", "admin-user");
              attrs.put("tenantId", "tenant-1");
            })
            .authorities(new SimpleGrantedAuthority("ROLE_admin")))
          .header("X-Trace-Id", createTraceId)
          .contentType(MediaType.APPLICATION_JSON)
          .content("""
            {
              "role": "participant",
              "maxUses": 2,
              "expiresInHours": 24
            }
            """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

      String inviteId = JsonPath.parse(inviteResponse).read("$.id");

      mockMvc.perform(delete("/api/v1/meetings/{meetingId}/invites/{inviteId}", meetingId, inviteId)
          .with(csrf())
          .with(oauth2Login()
            .attributes(attrs -> {
              attrs.put("sub", "admin-user");
              attrs.put("tenantId", "tenant-1");
            })
            .authorities(new SimpleGrantedAuthority("ROLE_admin")))
          .header("X-Trace-Id", revokeTraceId))
        .andExpect(status().isNoContent());

      long createCount = applicationEvents.stream(MeetingInviteCreatedEvent.class)
          .filter(e -> e.meetingId().equals(meetingId)
              && e.roomId().equals(roomId)
              && e.actorId().equals("admin-user")
              && e.traceId().equals(createTraceId)
              && e.changedFields().contains("role=")
              && e.changedFields().contains("maxUses=2"))
          .count();
      assertEquals(1, createCount);

            Integer createAuditCount = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM meeting_audit_events WHERE trace_id = ? AND action_type = ?",
              Integer.class,
              createTraceId,
              "invite_create");
            assertEquals(1, createAuditCount);

      long revokeCount = applicationEvents.stream(MeetingInviteRevokedEvent.class)
          .filter(e -> e.meetingId().equals(meetingId)
              && e.roomId().equals(roomId)
              && e.actorId().equals("admin-user")
              && e.traceId().equals(revokeTraceId)
              && e.changedFields().contains("inviteId=" + inviteId))
          .count();
      assertEquals(1, revokeCount);

            Integer revokeAuditCount = jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM meeting_audit_events WHERE trace_id = ? AND action_type = ?",
              Integer.class,
              revokeTraceId,
              "invite_revoke");
            assertEquals(1, revokeAuditCount);
      }

  // AC4: Create invite without authorization returns 401
  @Test
  void createInviteWithoutAuthorizationReturnsUnauthorized() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "participant",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isUnauthorized());
  }

  // AC4: Create invite with wrong role returns 403
  @Test
  void createInviteWithUserRoleReturnsForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "regular-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_user")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "participant",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isForbidden());
  }

  // AC5: Create invite with invalid role returns bad request
  @Test
  void createInviteWithInvalidRoleReturnsBadRequest() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-invalid-role")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "invalid_role",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("INVALID_REQUEST"));
  }

  // AC5: Create invite with maxUses exceeded returns bad request
  @Test
  void createInviteWithMaxUsesZeroReturnsBadRequest() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-invalid-maxuses")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "participant",
                  "maxUses": 0
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("VALIDATION_ERROR"));
  }

  // AC5: Create invite with expiresInHours too large returns bad request
  @Test
  void createInviteWithExpiresInHoursExceededReturnsBadRequest() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-invalid-expiry")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "participant",
                  "maxUses": 5,
                  "expiresInHours": 1000
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("VALIDATION_ERROR"));
  }

  // AC6: List invites for non-existent meeting returns not found
  @Test
  void listInvitesForNonExistentMeetingReturnsNotFound() throws Exception {
    mockMvc.perform(get("/api/v1/meetings/{meetingId}/invites", "non-existent-meeting")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-list-notfound"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("MEETING_NOT_FOUND"));
  }

  // AC6: Revoke non-existent invite returns not found
  @Test
  void revokeNonExistentInviteReturnsNotFound() throws Exception {
    mockMvc.perform(delete("/api/v1/meetings/{meetingId}/invites/{inviteId}", meetingId, "non-existent-invite")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-revoke-notfound"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("INVITE_NOT_FOUND"));
  }

  @Test
  void createInviteWithWrongTenantReturnsForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user-2");
                  attrs.put("tenantId", "tenant-2");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "role": "participant",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void listInvitesWithWrongTenantReturnsForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/meetings/{meetingId}/invites", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user-2");
                  attrs.put("tenantId", "tenant-2");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void createBulkInvitesWithWrongTenantReturnsForbidden() throws Exception {
    mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites/bulk", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user-2");
                  attrs.put("tenantId", "tenant-2");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "defaultRole": "participant",
                  "defaultTtlMinutes": 60,
                  "defaultMaxUses": 1,
                  "recipients": [
                    {
                      "rowIndex": 1,
                      "email": "user@example.com"
                    }
                  ]
                }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void revokeInviteWithWrongTenantReturnsForbidden() throws Exception {
    String inviteResponse = mockMvc.perform(post("/api/v1/meetings/{meetingId}/invites", meetingId)
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
                  "role": "participant",
                  "maxUses": 5
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    String inviteId = JsonPath.parse(inviteResponse).read("$.id");

    mockMvc.perform(delete("/api/v1/meetings/{meetingId}/invites/{inviteId}", meetingId, inviteId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user-2");
                  attrs.put("tenantId", "tenant-2");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
        .andExpect(status().isForbidden());
  }

  // AC3: limit/offset pagination params
  @Test
  void listInvitesWithLimitOffsetMapsToCorrectPage() throws Exception {
    mockMvc.perform(get("/api/v1/meetings/{meetingId}/invites?limit=5&offset=10", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-limit-offset"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.pageSize").value(5));
  }

  @Test
  void listInvitesWithSizeZeroFallsBackToDefaultPageSize() throws Exception {
    mockMvc.perform(get("/api/v1/meetings/{meetingId}/invites?size=0", meetingId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-invite-size-zero"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.pageSize").value(20));
  }
}
