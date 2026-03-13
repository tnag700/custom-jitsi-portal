package com.acme.jitsi.domains.health.dto;

import java.util.List;

public record JoinReadinessCheckResponse(
    String key,
    String status,
    String headline,
    String reason,
    List<String> actions,
    String errorCode,
    boolean blocking) {
}