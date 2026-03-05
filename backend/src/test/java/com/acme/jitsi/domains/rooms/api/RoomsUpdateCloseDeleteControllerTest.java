package com.acme.jitsi.domains.rooms.api;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.rooms.event.RoomClosedEvent;
import com.acme.jitsi.domains.rooms.event.RoomDeletedEvent;
import com.acme.jitsi.domains.rooms.event.RoomUpdatedEvent;
import com.acme.jitsi.shared.JwtTestProperties;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
    })
@AutoConfigureMockMvc
@RecordApplicationEvents
class RoomsUpdateCloseDeleteControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ApplicationEvents applicationEvents;

  private OAuth2LoginRequestPostProcessor adminLogin() {
    return oauth2Login()
        .attributes(attrs -> attrs.put("sub", "admin-user"))
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));
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
            .with(adminLogin())
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
                  "title": "Active meeting",
                  "description": "Room occupancy test",
                  "meetingType": "scheduled",
                  "startsAt": "%s",
                  "endsAt": "%s",
                  "allowGuests": true,
                  "recordingEnabled": false
                }
                """.formatted(
                Instant.now().plusSeconds(3600),
                Instant.now().plusSeconds(7200))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    return JsonPath.parse(response).read("$.meetingId");
  }

  // AC3: Update room parameters
  @Test
  void updateRoomChangesParameters() throws Exception {
    String roomId = createRoom("Room To Update", "tenant-1");

    mockMvc.perform(put("/api/v1/rooms/" + roomId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-room-update-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Updated Room Name",
                  "description": "Updated description",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.roomId").value(roomId))
        .andExpect(jsonPath("$.name").value("Updated Room Name"))
        .andExpect(jsonPath("$.description").value("Updated description"))
        .andExpect(jsonPath("$.tenantId").value("tenant-1"))
        .andExpect(jsonPath("$.configSetId").value("config-1"));

      long eventCount = applicationEvents.stream(RoomUpdatedEvent.class)
          .filter(e -> e.roomId().equals(roomId)
              && e.actorId().equals("admin-user")
              && e.traceId().equals("trace-room-update-1")
              && e.changedFields().equals("name,description,configSetId")
              && e.oldValues().contains("name=Room To Update")
              && e.oldValues().contains("configSetId=config-1")
              && e.newValues().contains("name=Updated Room Name")
              && e.newValues().contains("description=Updated description")
              && e.newValues().contains("configSetId=config-1"))
          .count();
      assertEquals(1, eventCount);
  }

  @Test
    void listRoomsWithSizeZeroFallsBackToDefaultPageSize() throws Exception {
    mockMvc.perform(get("/api/v1/rooms?tenantId=tenant-1&size=0")
        .with(csrf())
        .with(adminLogin("tenant-1"))
        .header("X-Trace-Id", "trace-room-list-invalid-size"))
      .andExpect(status().isOk())
      .andExpect(content().contentType(MediaType.APPLICATION_JSON))
      .andExpect(jsonPath("$.pageSize").value(20));
  }

  // AC3: Update room with duplicate name returns ROOM_NAME_CONFLICT
  @Test
  void updateRoomWithDuplicateNameReturnsConflict() throws Exception {
    createRoom("Existing Room", "tenant-1");
    String roomIdToUpdate = createRoom("Room To Rename", "tenant-1");

    mockMvc.perform(put("/api/v1/rooms/" + roomIdToUpdate)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-room-update-dup-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Existing Room",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ROOM_NAME_CONFLICT"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-update-dup-1"));
  }

  // AC4: Close room without active meetings succeeds
  @Test
  void closeRoomWithoutActiveMeetingsSucceeds() throws Exception {
    String roomId = createRoom("Room To Close", "tenant-1");

    mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-room-close-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.roomId").value(roomId))
        .andExpect(jsonPath("$.status").value("closed"));

      long eventCount = applicationEvents.stream(RoomClosedEvent.class)
          .filter(e -> e.roomId().equals(roomId)
              && e.actorId().equals("admin-user")
              && e.traceId().equals("trace-room-close-1")
              && e.changedFields().equals("status")
              && e.oldValues().equals("status=ACTIVE")
              && e.newValues().equals("status=CLOSED"))
          .count();
      assertEquals(1, eventCount);
  }

  // AC4: Delete room without active meetings succeeds
  @Test
  void deleteRoomWithoutActiveMeetingsSucceeds() throws Exception {
    String roomId = createRoom("Room To Delete", "tenant-1");

    mockMvc.perform(delete("/api/v1/rooms/" + roomId)
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-room-delete-1"))
        .andExpect(status().isNoContent());

    // Verify room is deleted
    mockMvc.perform(get("/api/v1/rooms/" + roomId)
            .with(csrf())
            .with(adminLogin()))
        .andExpect(status().isNotFound());

    long eventCount = applicationEvents.stream(RoomDeletedEvent.class)
        .filter(e -> e.roomId().equals(roomId)
            && e.actorId().equals("admin-user")
            && e.traceId().equals("trace-room-delete-1")
            && e.changedFields().equals("name,tenantId,configSetId,status")
            && e.oldValues().contains("name=Room To Delete")
            && e.oldValues().contains("tenantId=tenant-1")
            && e.oldValues().contains("configSetId=config-1")
            && e.oldValues().contains("status=ACTIVE")
            && e.newValues().equals("-"))
        .count();
    assertEquals(1, eventCount);
  }

  // AC4: Delete room with active/future meetings returns conflict
  @Test
  void deleteRoomWithActiveMeetingsReturnsError() throws Exception {
    String roomId = createRoom("Room With Meetings", "tenant-1");
    createMeeting(roomId, "tenant-1");

    mockMvc.perform(delete("/api/v1/rooms/" + roomId)
        .with(csrf())
        .with(adminLogin("tenant-1"))
        .header("X-Trace-Id", "trace-room-delete-active-1"))
      .andExpect(status().isConflict())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value("ROOM_HAS_ACTIVE_MEETINGS"))
      .andExpect(jsonPath("$.properties.traceId").value("trace-room-delete-active-1"));
  }

  @Test
  void closeRoomWithActiveMeetingsReturnsError() throws Exception {
    String roomId = createRoom("Room With Meetings", "tenant-2");
    createMeeting(roomId, "tenant-2");

    mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
        .with(csrf())
        .with(adminLogin("tenant-2"))
        .header("X-Trace-Id", "trace-room-close-active-1"))
      .andExpect(status().isConflict())
      .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value("ROOM_HAS_ACTIVE_MEETINGS"))
      .andExpect(jsonPath("$.properties.traceId").value("trace-room-close-active-1"));
  }

  @Test
  void getRoomWithMismatchedTenantClaimReturnsForbidden() throws Exception {
    String roomId = createRoom("Tenant Scoped Room", "tenant-1");

    mockMvc.perform(get("/api/v1/rooms/" + roomId)
            .with(csrf())
            .with(adminLogin("tenant-2"))
            .header("X-Trace-Id", "trace-room-get-tenant-mismatch"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value("TENANT_ACCESS_DENIED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-get-tenant-mismatch"));
  }

  // Update non-existent room returns 404
  @Test
  void updateNonExistentRoomReturns404() throws Exception {
    mockMvc.perform(put("/api/v1/rooms/non-existent-room-id")
            .with(csrf())
            .with(adminLogin())
            .header("X-Trace-Id", "trace-room-update-notfound-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Updated Name",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ROOM_NOT_FOUND"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-update-notfound-1"));
  }

  // Close already closed room returns error
  @Test
  void closeAlreadyClosedRoomReturnsError() throws Exception {
    String roomId = createRoom("Room To Close Twice", "tenant-1");

    // First close
    mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
            .with(csrf())
            .with(adminLogin("tenant-1")))
        .andExpect(status().isOk());

    // Second close attempt
    mockMvc.perform(post("/api/v1/rooms/" + roomId + "/close")
            .with(csrf())
            .with(adminLogin("tenant-1"))
            .header("X-Trace-Id", "trace-room-close-twice-1"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ROOM_ALREADY_CLOSED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-close-twice-1"));
  }
}
