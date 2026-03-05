package com.acme.jitsi.domains.health.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityStateService;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultHealthService implements HealthService {

  private final ConfigSetCompatibilityStateService compatibilityStateService;

  public DefaultHealthService(ConfigSetCompatibilityStateService compatibilityStateService) {
    this.compatibilityStateService = compatibilityStateService;
  }

  @Override
  public HealthResponse getHealth() {
    return compatibilityStateService.findLatestIncompatibleActive()
        .map(this::createDownResponse)
        .orElseGet(() -> new HealthResponse("UP", "COMPATIBLE", null, null, null, null));
  }

  private HealthResponse createDownResponse(ConfigSetCompatibilityCheck check) {
    String diagnostics = check.mismatchCodes().stream()
        .map(code -> "MISMATCH: " + code)
        .collect(Collectors.joining(" | "));
    return new HealthResponse(
        "DOWN",
        "INCOMPATIBLE",
        check.configSetId(),
        diagnostics,
        check.traceId(),
        check.checkedAt().toString());
  }
}
