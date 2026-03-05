package com.acme.jitsi.domains.configsets.event;

import java.util.List;

public record ConfigSetCompatibilityCheckedEvent(
    String configSetId,
    String actorId,
    String traceId,
    boolean compatible,
    List<String> mismatchCodes,
    String details) {
}