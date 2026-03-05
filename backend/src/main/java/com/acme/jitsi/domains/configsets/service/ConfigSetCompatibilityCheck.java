package com.acme.jitsi.domains.configsets.service;

import java.time.Instant;
import java.util.List;

public record ConfigSetCompatibilityCheck(
    String checkId,
    String configSetId,
    boolean compatible,
    List<String> mismatchCodes,
    String details,
    Instant checkedAt,
    String traceId) {

  public ConfigSetCompatibilityCheck {
    mismatchCodes = mismatchCodes == null ? List.of() : List.copyOf(mismatchCodes);
  }
}