package com.acme.jitsi.domains.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.admin.dto.AdminIncidentCoordinationUpdateRequest;
import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.admin.service.AdminIncidentsService;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.JwtTestProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:adminincidents;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
    })
@AutoConfigureMockMvc
class AdminIncidentsSecurityIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminIncidentsService adminIncidentsService;

  @Test
  void adminWithTenantClaimCanAccessIncidents() throws Exception {
    when(adminIncidentsService.listIncidents(anyString(), any(), any()))
        .thenReturn(new AdminIncidentListResponse(
            "15m",
            "dev",
            "tenant-1",
            "2026-03-18T10:00:00Z",
        "active",
        null,
        List.of(),
        List.of(),
        new AdminIncidentListResponse.QueueSort("queue", "Severity + freshness", "desc"),
            50,
            0,
            0,
            List.of()));

    var adminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));

    mockMvc.perform(get("/api/v1/admin/incidents").with(adminLogin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value("tenant-1"));
  }

  @Test
  void adminCanCreateIncidentTicket() throws Exception {
    when(adminIncidentsService.createTicket(anyString(), any(), anyString(), anyString(), anyString()))
        .thenReturn(new AdminIncidentTicketResponse(
            true,
            true,
            "INC-42",
            "https://tickets.example.test/INC-42",
            "TOKEN_INVALID incident",
            null));

    var adminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/ticket")
            .param("environment", "dev")
            .with(csrf())
            .with(adminLogin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(true))
        .andExpect(jsonPath("$.ticketKey").value("INC-42"));
  }

  @Test
  void adminCanUpdateIncidentCoordination() throws Exception {
    when(adminIncidentsService.updateCoordination(
        anyString(),
        any(),
        anyString(),
        anyString(),
        any(AdminIncidentCoordinationUpdateRequest.class),
        anyString()))
            .thenReturn(new AdminIncidentDetailResponse.CoordinationState(
                true,
                "available",
                "Coordination seam remains lightweight and optional.",
                "lead.support",
                "investigating",
                null,
                "not-linked",
                null,
                List.of()));

    var adminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/coordination")
            .param("environment", "dev")
            .with(csrf())
            .with(adminLogin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "owner": "lead.support",
                  "workflowStatus": "investigating"
                }
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enabled").value(true))
        .andExpect(jsonPath("$.owner").value("lead.support"))
        .andExpect(jsonPath("$.workflowStatus").value("investigating"));
  }

  @Test
  void supportEngineerWithTenantClaimCanAccessIncidents() throws Exception {
    when(adminIncidentsService.listIncidents(anyString(), any(), any()))
        .thenReturn(new AdminIncidentListResponse(
            "15m",
            "all",
            "tenant-1",
            "2026-03-18T10:00:00Z",
        "active",
        null,
        List.of(),
        List.of(),
        new AdminIncidentListResponse.QueueSort("queue", "Severity + freshness", "desc"),
            50,
            0,
            0,
            List.of()));

    var supportEngineerLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "support-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"));

    mockMvc.perform(get("/api/v1/admin/incidents").with(supportEngineerLogin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value("tenant-1"));
  }

  @Test
  void supportEngineerCannotCreateIncidentTicket() throws Exception {
    var supportEngineerLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "support-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"));

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/ticket").with(csrf()).with(supportEngineerLogin))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
  }

  @Test
  void supportEngineerCannotUpdateIncidentCoordination() throws Exception {
    var supportEngineerLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "support-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"));

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/coordination")
            .with(csrf())
            .with(supportEngineerLogin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "owner": "lead.support",
                  "workflowStatus": "investigating"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
  }

  @Test
  void authenticatedNonAdminGets403ForIncidents() throws Exception {
    var userLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "user-1");
          attrs.put("tenantId", "tenant-1");
        });

    mockMvc.perform(get("/api/v1/admin/incidents").with(userLogin))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()));
  }
}