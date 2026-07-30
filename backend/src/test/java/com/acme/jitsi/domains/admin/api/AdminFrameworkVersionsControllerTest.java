package com.acme.jitsi.domains.admin.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.admin.dto.AdminFrameworkVersionsResponse;
import com.acme.jitsi.domains.admin.service.FrameworkVersionMonitorService;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminFrameworkVersionsController.class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("slice")
class AdminFrameworkVersionsControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private FrameworkVersionMonitorService monitorService;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @Test
  void getReturnsNormalizedVersionSnapshot() throws Exception {
    when(monitorService.getCurrent()).thenReturn(response());

    mockMvc.perform(get("/api/v1/admin/framework-versions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scanStatus").value("current"))
        .andExpect(jsonPath("$.criticalUpdateRequired").value(true))
        .andExpect(jsonPath("$.components[0].displayName").value("Qwik"))
        .andExpect(jsonPath("$.components[0].advisories[0].id").value("GHSA-test"));
  }

  @Test
  void postRefreshReturnsNewSnapshot() throws Exception {
    when(monitorService.refresh()).thenReturn(response());

    mockMvc.perform(post("/api/v1/admin/framework-versions/refresh"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.criticalVulnerabilityCount").value(1));
  }

  private AdminFrameworkVersionsResponse response() {
    Instant checkedAt = Instant.parse("2026-07-30T10:00:00Z");
    return new AdminFrameworkVersionsResponse(
        checkedAt,
        checkedAt,
        checkedAt.plusSeconds(21_600),
        "current",
        "Versions checked.",
        true,
        1,
        1,
        List.of(new AdminFrameworkVersionsResponse.Component(
            "qwik",
            "Qwik",
            "npm",
            "@qwik.dev/core",
            "2.0.0-beta.38",
            "build-config",
            "current",
            "critical",
            1,
            1,
            List.of(new AdminFrameworkVersionsResponse.Advisory(
                "GHSA-test",
                List.of("CVE-2026-12345"),
                "Critical issue",
                "critical",
                List.of("2.0.0"),
                "https://osv.dev/vulnerability/GHSA-test",
                "2026-07-29T10:00:00Z")))));
  }
}
