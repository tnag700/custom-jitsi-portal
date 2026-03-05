package com.acme.jitsi.domains.configsets.service;

import java.time.Instant;
import java.util.List;

public record ConfigCompatibilityCheckResult(
    boolean compatible,
    List<ConfigCompatibilityMismatch> mismatches,
    Instant checkedAt,
    String traceId) {

  public ConfigCompatibilityCheckResult {
    mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
  }
}