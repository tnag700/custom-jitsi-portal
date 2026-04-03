package com.acme.jitsi.domains.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.admin.dto.AdminRoleHistoryResponse;
import com.acme.jitsi.domains.admin.service.AdminRoleHistoryService;
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
      "spring.datasource.url=jdbc:h2:mem:adminrolehistory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class AdminRoleHistorySecurityIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminRoleHistoryService adminRoleHistoryService;

  @Test
  void supportEngineerWithTenantClaimCanAccessRoleHistory() throws Exception {
    when(adminRoleHistoryService.getRoleHistory(anyString(), any(), any()))
        .thenReturn(new AdminRoleHistoryResponse("tenant-1", "dev", "2026-03-19T10:00:00Z", 0, 20, 0, 0, List.of()));

    var supportEngineerLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "support-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_support-engineer"));

    mockMvc.perform(get("/api/v1/admin/role-history").param("meetingId", "meeting-1").with(supportEngineerLogin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value("tenant-1"));
  }

  @Test
  void systemAdminWithTenantClaimCanAccessRoleHistory() throws Exception {
    when(adminRoleHistoryService.getRoleHistory(anyString(), any(), any()))
        .thenReturn(new AdminRoleHistoryResponse("tenant-1", "dev", "2026-03-19T10:00:00Z", 0, 20, 0, 0, List.of()));

    var systemAdminLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "system-admin-user");
          attrs.put("tenantId", "tenant-1");
        })
        .authorities(new SimpleGrantedAuthority("ROLE_system-admin"));

    mockMvc.perform(get("/api/v1/admin/role-history").param("meetingId", "meeting-1").with(systemAdminLogin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value("tenant-1"));
  }

  @Test
  void authenticatedNonAdminGets403ForRoleHistory() throws Exception {
    var userLogin = oauth2Login()
        .attributes(attrs -> {
          attrs.put("sub", "user-1");
          attrs.put("tenantId", "tenant-1");
        });

    mockMvc.perform(get("/api/v1/admin/role-history").param("meetingId", "meeting-1").with(userLogin))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
      .andExpect(jsonPath("$.properties.errorCode").value(ErrorCode.ACCESS_DENIED.code()))
      .andExpect(jsonPath("$.properties.traceId").isNotEmpty());
  }
}