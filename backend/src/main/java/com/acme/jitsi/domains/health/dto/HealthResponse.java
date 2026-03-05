package com.acme.jitsi.domains.health.dto;

import org.jspecify.annotations.Nullable;

public record HealthResponse(
	String status,
	@Nullable String compatibilityStatus,
	@Nullable String configSetId,
	@Nullable String details,
	@Nullable String traceId,
	@Nullable String checkedAt) {
}
