package com.acme.jitsi.domains.health.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessCheckResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import com.acme.jitsi.domains.health.service.HealthService;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HealthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Tag("slice")
class HealthControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private HealthService healthService;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @Test
  void healthEndpointReturnsUp() throws Exception {
    when(healthService.getHealth()).thenReturn(new HealthResponse(
        "UP",
        null,
        null,
        null,
        null,
        null));

    mockMvc.perform(get("/api/v1/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void joinReadinessEndpointReturnsStructuredSnapshot() throws Exception {
    when(problemResponseFacade.resolveTraceId(any())).thenReturn("trace-health-1");
    when(healthService.getJoinReadiness("trace-health-1")).thenReturn(new JoinReadinessResponse(
        "ready",
        "2026-03-12T12:00:00Z",
        "trace-health-1",
        "https://portal.example.test/join/demo",
      List.of(new JoinReadinessCheckResponse(
        "redis",
        "up",
        "OK",
        null,
        List.of(),
        null,
        false))));

    mockMvc.perform(get("/api/v1/health/join-readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ready"))
        .andExpect(jsonPath("$.traceId").value("trace-health-1"))
        .andExpect(jsonPath("$.checkedAt").value("2026-03-12T12:00:00Z"))
        .andExpect(jsonPath("$.systemChecks").isArray());
  }

}
