package com.acme.jitsi.domains.rooms.api;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.rooms.event.RoomCreatedEvent;
import com.acme.jitsi.shared.JwtTestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
class RoomsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ApplicationEvents applicationEvents;

  // AC1: Unauthenticated request returns 401 with stable problem contract
  @Test
  void unauthenticatedCreateRoomReturns401WithStableProblemContract() throws Exception {
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .header("X-Trace-Id", "trace-room-auth-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Conference Room A",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value("/api/v1/rooms"))
        .andExpect(jsonPath("$.properties.errorCode").value("AUTH_REQUIRED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-auth-1"));
  }

  // AC1: Non-admin user gets 403 Forbidden
  @Test
  void nonAdminCreateRoomReturns403Forbidden() throws Exception {
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "regular-user")))
            .header("X-Trace-Id", "trace-room-forbidden-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Conference Room A",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ACCESS_DENIED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-forbidden-1"));
  }

  // AC1: Create room with required fields succeeds (with admin role)
  @Test
  void createRoomWithRequiredFieldsSucceeds() throws Exception {
    String createResponse = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-create-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Conference Room A",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.roomId").isString())
        .andExpect(jsonPath("$.name").value("Conference Room A"))
        .andExpect(jsonPath("$.tenantId").value("tenant-1"))
        .andExpect(jsonPath("$.status").value("active"))
        .andExpect(jsonPath("$.configSetId").value("config-1"))
        .andReturn().getResponse().getContentAsString();

      String roomId = com.jayway.jsonpath.JsonPath.parse(createResponse).read("$.roomId");

      long eventCount = applicationEvents.stream(RoomCreatedEvent.class)
          .filter(e -> e.roomId().equals(roomId)
              && e.actorId().equals("admin-user")
              && e.traceId().equals("trace-room-create-1")
              && e.changedFields().equals("name,description,tenantId,configSetId,status")
              && e.oldValues().equals("-")
              && e.newValues().contains("name=Conference Room A")
              && e.newValues().contains("tenantId=tenant-1")
              && e.newValues().contains("configSetId=config-1")
              && e.newValues().contains("status=ACTIVE"))
          .count();
      assertEquals(1, eventCount);
  }

  // AC1: Create room without required fields returns 400
  @Test
  void createRoomWithoutRequiredFieldsReturns400() throws Exception {
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-validation-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "description": "Missing required fields"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-validation-1"));
  }

  // AC2: Duplicate room name in same tenant returns ROOM_NAME_CONFLICT
  @Test
  void createRoomWithDuplicateNameInSameTenantReturnsConflict() throws Exception {
    // First create
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-dup-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Duplicate Room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated());

    // Second create with same name in same tenant
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-dup-2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Duplicate Room",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ROOM_NAME_CONFLICT"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-dup-2"));
  }

  @Test
  void createRoomWithDuplicateCyrillicNameInSameTenantReturnsConflict() throws Exception {
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-dup-cyrillic-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "тест",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated());

    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-dup-cyrillic-2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "тест",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ROOM_NAME_CONFLICT"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-dup-cyrillic-2"));
  }

  // AC2: Same room name in different tenant is allowed
  @Test
  void createRoomWithSameNameInDifferentTenantSucceeds() throws Exception {
    // Create in tenant-1
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-tenant-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Shared Room Name",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated());

    // Create with same name in tenant-2
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-tenant-2")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Shared Room Name",
                  "tenantId": "tenant-2",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.tenantId").value("tenant-2"));
  }

  // AC5: Invalid config-set returns CONFIG_SET_INVALID
  @Test
  void createRoomWithInvalidConfigSetReturnsError() throws Exception {
    mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-config-1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Room With Bad Config",
                  "tenantId": "tenant-1",
                  "configSetId": "invalid-config"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("CONFIG_SET_INVALID"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-config-1"));
  }

  // List rooms
  @Test
  void listRoomsReturnsPagedResponse() throws Exception {
    mockMvc.perform(get("/api/v1/rooms?tenantId=tenant-1")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-list-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").isNumber())
        .andExpect(jsonPath("$.pageSize").isNumber())
        .andExpect(jsonPath("$.totalElements").isNumber());
  }

  @Test
  void listRoomsWithMismatchedTenantClaimReturnsForbidden() throws Exception {
    mockMvc.perform(get("/api/v1/rooms?tenantId=tenant-1")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-2");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-list-tenant-mismatch"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("TENANT_ACCESS_DENIED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-list-tenant-mismatch"));
  }

  @Test
  void listRoomsWithoutTenantClaimReturnsForbiddenWithExplicitTenantErrorCode() throws Exception {
    mockMvc.perform(get("/api/v1/rooms?tenantId=tenant-1")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-list-tenant-missing"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("TENANT_CLAIM_REQUIRED"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-list-tenant-missing"));
  }

  // Get room by ID
  @Test
  void getRoomByIdReturnsRoom() throws Exception {
    // First create a room
    String response = mockMvc.perform(post("/api/v1/rooms")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-get-create")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "name": "Room To Get",
                  "tenantId": "tenant-1",
                  "configSetId": "config-1"
                }
                """))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();

    // Extract roomId from response
    String roomId = com.jayway.jsonpath.JsonPath.parse(response).read("$.roomId");

    // Get the room
    mockMvc.perform(get("/api/v1/rooms/" + roomId)
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-get-1"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.roomId").value(roomId))
        .andExpect(jsonPath("$.name").value("Room To Get"));
  }

  // Get non-existent room returns 404
  @Test
  void getNonExistentRoomReturns404() throws Exception {
    mockMvc.perform(get("/api/v1/rooms/non-existent-room-id")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "admin-user"))
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-notfound-1"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value("ROOM_NOT_FOUND"))
        .andExpect(jsonPath("$.properties.traceId").value("trace-room-notfound-1"));
  }

  // AC3: limit/offset pagination params
  @Test
  void listRoomsWithLimitOffsetMapsToCorrectPage() throws Exception {
    mockMvc.perform(get("/api/v1/rooms?tenantId=tenant-1&limit=10&offset=20")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-limit-offset"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.pageSize").value(10));
  }

  @Test
  void listRoomsWithSizeZeroFallsBackToDefaultPageSize() throws Exception {
    mockMvc.perform(get("/api/v1/rooms?tenantId=tenant-1&size=0")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> {
                  attrs.put("sub", "admin-user");
                  attrs.put("tenantId", "tenant-1");
                })
                .authorities(new SimpleGrantedAuthority("ROLE_admin")))
            .header("X-Trace-Id", "trace-room-size-zero"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.pageSize").value(20));
  }
}
