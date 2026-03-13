package com.acme.jitsi.domains.health.service;

import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;

public interface HealthService {
  HealthResponse getHealth();

  JoinReadinessResponse getJoinReadiness(String traceId);
}
