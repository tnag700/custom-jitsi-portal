package com.acme.jitsi.domains.health.api;

import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import com.acme.jitsi.domains.health.service.HealthService;
import com.acme.jitsi.security.ProblemResponseFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/health", version = "v1")
public class HealthController {

  private final HealthService healthService;
  private final ProblemResponseFacade problemResponseFacade;

  public HealthController(HealthService healthService, ProblemResponseFacade problemResponseFacade) {
    this.healthService = healthService;
    this.problemResponseFacade = problemResponseFacade;
  }

  @GetMapping
  public HealthResponse getHealth() {
    return healthService.getHealth();
  }

  @GetMapping("/join-readiness")
  public JoinReadinessResponse getJoinReadiness(HttpServletRequest request) {
    return healthService.getJoinReadiness(problemResponseFacade.resolveTraceId(request));
  }
}
