package com.acme.jitsi.domains.configsets.service;

import java.time.Instant;
import java.util.List;

public record DryRunResult(
    boolean valid,
    List<String> errors,
    List<ConfigCompatibilityMismatch> mismatches,
    Instant checkedAt,
    String traceId) {

  public DryRunResult {
    errors = errors == null ? List.of() : List.copyOf(errors);
    mismatches = mismatches == null ? List.of() : List.copyOf(mismatches);
  }

  public static DryRunResult success() {
    return new DryRunResult(true, List.of(), List.of(), Instant.now(), null);
  }

  public static DryRunResult failure(List<String> errors) {
    return new DryRunResult(false, errors, List.of(), Instant.now(), null);
  }

  public static DryRunResult fromCompatibilityResult(ConfigCompatibilityCheckResult compatibilityResult) {
    List<String> errorMessages = compatibilityResult.mismatches()
        .stream()
        .map(ConfigCompatibilityMismatch::message)
        .toList();
    return new DryRunResult(
        compatibilityResult.compatible(),
        errorMessages,
        compatibilityResult.mismatches(),
        compatibilityResult.checkedAt(),
        compatibilityResult.traceId());
  }
}