package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import com.acme.jitsi.domains.admin.dto.AdminFrameworkVersionsResponse;
import com.acme.jitsi.domains.admin.service.FrameworkVersionMonitorService;
import java.time.Instant;
import java.util.List;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb_security_matrix;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
      "app.invites.mode=properties",
      "app.invites.exchange.atomic-store=in-memory",
      "app.invites.exchange.invites[0].token=invite-valid",
      "app.invites.exchange.invites[0].meeting-id=meeting-a",
      "app.invites.exchange.invites[0].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[0].usage-limit=1",
      "app.invites.exchange.known-meeting-ids=meeting-a",
      "app.rooms.valid-config-sets=config-1,config-2",
      "app.rooms.config-sets.config-1.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-1.audience=jitsi-meet",
      "app.rooms.config-sets.config-1.role-claim=role",
      "app.rooms.config-sets.config-2.issuer=https://portal.example.test",
      "app.rooms.config-sets.config-2.audience=jitsi-meet",
      "app.rooms.config-sets.config-2.role-claim=role",
      "APP_CONFIG_SETS_ENCRYPTION_KEY=0123456789ABCDEF0123456789ABCDEF"
    })
@AutoConfigureMockMvc
class SecurityConfigRouteMatrixTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private StringRedisTemplate redisTemplate;

  @MockitoBean
  private ValueOperations<String, String> valueOperations;

  @MockitoBean
  private FrameworkVersionMonitorService frameworkVersionMonitorService;

  @BeforeEach
  void setUp() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
  }

  @Test
  void authEndpointsArePublic() throws Exception {
    mockMvc.perform(get("/api/v1/auth/login"))
      .andExpect(status().isFound())
      .andExpect(header().string("Location", "/oauth2/authorization/keycloak"));
    mockMvc.perform(get("/api/v1/auth/error").param("code", ErrorCode.ACCESS_DENIED.code()))
      .andExpect(status().isForbidden())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()));
    mockMvc.perform(get("/api/v1/auth/csrf"))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.token").isNotEmpty())
      .andExpect(jsonPath("$.headerName").isNotEmpty());
    mockMvc.perform(post("/api/v1/auth/refresh")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/auth/refresh"));
  }

  @Test
  void healthEndpointsArePublic() throws Exception {
    mockMvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/health/join-readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void prometheusEndpointIsNotPublicWhenAdvancedMonitoringIsDisabled() throws Exception {
    mockMvc.perform(get("/actuator/prometheus"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.instance").value("/actuator/prometheus"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()));
  }

  @Test
  void baselineSecurityHeadersArePresentOnPublicApiResponses() throws Exception {
    mockMvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("X-Frame-Options", "DENY"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
        .andExpect(header().string(
            "Content-Security-Policy",
            org.hamcrest.Matchers.containsString("default-src 'none'")))
        .andExpect(header().string(
            "Content-Security-Policy",
            org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
        .andExpect(header().string(
            "Content-Security-Policy",
            org.hamcrest.Matchers.containsString("object-src 'none'")));
  }

  @Test
  void errorEndpointRemainsPermitAllInsteadOfBeingInterceptedBySecurity() throws Exception {
    mockMvc.perform(get("/error")
            .accept(MediaType.APPLICATION_JSON)
            .requestAttr("jakarta.servlet.error.status_code", 500)
            .requestAttr("jakarta.servlet.error.request_uri", "/api/v1/rooms"))
        .andExpect(status().isInternalServerError())
      .andExpect(jsonPath("$.status").value(500));
  }

  @Test
  void inviteEndpointsArePublic() throws Exception {
    mockMvc.perform(post("/api/v1/invites/exchange")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    mockMvc.perform(get("/api/v1/invites/token-1/validate"))
      .andExpect(status().isNotFound())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.instance").value("/api/v1/invites/token-1/validate"))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.INVITE_NOT_FOUND.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
  }

  @Test
  void corsPreflightAllowsIdempotencyKeyForProtectedApiMutations() throws Exception {
    mockMvc.perform(options("/api/v1/rooms")
            .header("Origin", "http://localhost:3000")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "Content-Type, X-XSRF-TOKEN, Idempotency-Key"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Headers", org.hamcrest.Matchers.containsString("Idempotency-Key")));
  }

  @Test
  void protectedEndpointsWithoutAuthenticationReturn401AndStableProblemContract() throws Exception {
    mockMvc.perform(get("/api/v1/rooms").param("tenantId", "tenant-1"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").exists())
        .andExpect(jsonPath("$.detail").exists())
        .andExpect(jsonPath("$.instance").value("/api/v1/rooms"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.AUTH_REQUIRED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
    mockMvc.perform(get("/api/v1/meetings/upcoming").param("tenantId", "tenant-1"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/config-sets").param("tenantId", "tenant-1"))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/profile/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/v1/auth/refresh/revoke")
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isUnauthorized());
    mockMvc.perform(get("/api/v1/users/search").param("tenant_id", "tenant-1").param("q", "a"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void adminCanAccessAdminOnlyEndpoints() throws Exception {
    var adminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));

    mockMvc.perform(get("/api/v1/rooms").param("tenantId", "tenant-1").with(adminLogin))
      .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/meetings/meeting-missing/cancel").with(csrf()).with(adminLogin))
      .andExpect(status().isNotFound())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

    mockMvc.perform(get("/api/v1/config-sets").param("tenantId", "tenant-1").with(adminLogin))
      .andExpect(status().isOk());
  }

  @Test
  void adminCabinetRolesCanReadVersionMonitorButOnlyAdminCanRefresh() throws Exception {
    when(frameworkVersionMonitorService.getCurrent()).thenReturn(frameworkVersionsResponse());
    when(frameworkVersionMonitorService.refresh()).thenReturn(frameworkVersionsResponse());
    var supportEngineerLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "support-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"));
    var adminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));

    mockMvc.perform(get("/api/v1/admin/framework-versions").with(supportEngineerLogin))
        .andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/admin/framework-versions/refresh")
            .with(csrf())
            .with(supportEngineerLogin))
        .andExpect(status().isForbidden());
    mockMvc.perform(post("/api/v1/admin/framework-versions/refresh")
            .with(csrf())
            .with(adminLogin))
        .andExpect(status().isOk());
  }

  @Test
  void supportEngineerCanReadConfigSetsButCannotAccessAdminOnlyMutations() throws Exception {
    var supportEngineerLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "support-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"));

    mockMvc.perform(get("/api/v1/config-sets").param("tenantId", "tenant-1").with(supportEngineerLogin))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/config-sets")
        .with(csrf())
        .with(supportEngineerLogin)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty());

    mockMvc.perform(post("/api/v1/meetings/meeting-missing/cancel").with(csrf()).with(supportEngineerLogin))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/ticket")
            .with(csrf())
            .with(supportEngineerLogin))
        .andExpect(status().isForbidden());
  }

  @Test
  void systemAdminCanReadConfigSetsButCannotAccessAdminOnlyMutations() throws Exception {
    var systemAdminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "system-admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_system-admin"));

    mockMvc.perform(get("/api/v1/config-sets").param("tenantId", "tenant-1").with(systemAdminLogin))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/config-sets")
        .with(csrf())
        .with(systemAdminLogin)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{}"))
      .andExpect(status().isForbidden())
      .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty());

    mockMvc.perform(post("/api/v1/meetings/meeting-missing/cancel").with(csrf()).with(systemAdminLogin))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/ticket")
            .with(csrf())
            .with(systemAdminLogin))
        .andExpect(status().isForbidden());
  }

  @Test
  void securityAdminCanReadOperationalEndpointsButCannotMutateThem() throws Exception {
    var securityAdminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "security-admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_security-admin"));

    mockMvc.perform(get("/api/v1/config-sets").param("tenantId", "tenant-1").with(securityAdminLogin))
        .andExpect(status().isOk());

    mockMvc.perform(get("/api/v1/admin/dashboard").with(securityAdminLogin))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/v1/config-sets")
            .with(csrf())
            .with(securityAdminLogin)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isForbidden());

    mockMvc.perform(post("/api/v1/admin/incidents/incident-1/ticket")
            .with(csrf())
            .with(securityAdminLogin))
        .andExpect(status().isForbidden());
  }

  @Test
  void authenticatedNonAdminGets403OnAdminOnlyEndpoints() throws Exception {
    var userLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "regular-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_participant"));

    mockMvc.perform(get("/api/v1/rooms").param("tenantId", "tenant-1").with(userLogin))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").exists())
        .andExpect(jsonPath("$.detail").exists())
        .andExpect(jsonPath("$.instance").value("/api/v1/rooms"))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
        .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
    mockMvc.perform(post("/api/v1/meetings/meeting-missing/cancel").with(csrf()).with(userLogin))
        .andExpect(status().isForbidden());
    mockMvc.perform(get("/api/v1/config-sets").param("tenantId", "tenant-1").with(userLogin))
        .andExpect(status().isForbidden());
  }

  @Test
  void tenantScopedEndpointsRejectARequestedTenantDifferentFromThePrincipal() throws Exception {
    var adminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "tenant-admin");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_admin"));

    mockMvc.perform(get("/api/v1/rooms").param("tenantId", "tenant-2").with(adminLogin))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TENANT_ACCESS_DENIED.code()));

    mockMvc.perform(get("/api/v1/users/search")
            .param("tenant_id", "tenant-2")
            .param("q", "a")
            .with(adminLogin))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.TENANT_ACCESS_DENIED.code()));
  }

  @Test
  void unknownPlatformRoleDoesNotGainAdministrativeAccess() throws Exception {
    var unknownRoleLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "organizer-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_organizer"));

    mockMvc.perform(get("/api/v1/rooms").param("tenantId", "tenant-1").with(unknownRoleLogin))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/v1/admin/dashboard").with(unknownRoleLogin))
        .andExpect(status().isForbidden());
  }

  @Test
  void undefinedEndpointIsDeniedByDefault() throws Exception {
    mockMvc.perform(get("/api/v1/non-existent-endpoint"))
        .andExpect(status().isUnauthorized());
  }

  private AdminFrameworkVersionsResponse frameworkVersionsResponse() {
    Instant now = Instant.parse("2026-07-30T10:00:00Z");
    return new AdminFrameworkVersionsResponse(
        now,
        now,
        now.plusSeconds(21_600),
        "current",
        "Versions checked.",
        false,
        0,
        0,
        List.of());
  }
}
