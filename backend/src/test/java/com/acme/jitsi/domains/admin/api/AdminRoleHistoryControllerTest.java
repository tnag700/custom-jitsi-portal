package com.acme.jitsi.domains.admin.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.admin.dto.AdminRoleHistoryResponse;
import com.acme.jitsi.domains.admin.service.AdminRoleHistoryService;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminRoleHistoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AdminRoleHistoryExceptionHandler.class)
@Tag("slice")
class AdminRoleHistoryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminRoleHistoryService adminRoleHistoryService;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @Test
  void listEndpointReturnsTypedRoleHistoryPayload() throws Exception {
    when(tenantAccessGuard.resolveTenantId(any())).thenReturn("tenant-1");
    when(adminRoleHistoryService.getRoleHistory(any(), any(), any()))
        .thenReturn(new AdminRoleHistoryResponse(
            "tenant-1",
            "dev",
            "2026-03-19T10:00:00Z",
            0,
            20,
            1,
            1,
            List.of(new AdminRoleHistoryResponse.RoleHistoryEntry(
                "2026-03-19T09:55:00Z",
                "update",
                "Изменение роли",
                "participant",
                "moderator",
                "Иван Иванов",
                "use***45",
                "Мария Петрова",
                "adm***01",
                "tenant-1",
                "dev",
                "room-1",
                "meeting-1",
                "trace-1"))));

    mockMvc.perform(get("/api/v1/admin/role-history")
            .param("meetingId", "meeting-1")
            .param("environment", "dev")
            .with(oauth2Login()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").value("tenant-1"))
        .andExpect(jsonPath("$.content[0].actionType").value("update"))
        .andExpect(jsonPath("$.content[0].oldRole").value("participant"))
        .andExpect(jsonPath("$.content[0].newRole").value("moderator"));
  }
}