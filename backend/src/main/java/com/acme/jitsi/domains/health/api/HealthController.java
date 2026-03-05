package com.acme.jitsi.domains.health.api;

import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.service.HealthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/health", version = "v1")
public class HealthController {

  private final HealthService healthService;

  public HealthController(HealthService healthService) {
    this.healthService = healthService;
  }

  @GetMapping
  public HealthResponse getHealth() {
    return healthService.getHealth();
  }
}
