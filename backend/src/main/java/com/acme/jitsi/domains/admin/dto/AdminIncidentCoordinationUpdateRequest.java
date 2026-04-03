package com.acme.jitsi.domains.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record AdminIncidentCoordinationUpdateRequest(
    @Schema(types = {"string", "null"})
    @Nullable String owner,
    @Nullable String workflowStatus,
    @Schema(types = {"string", "null"})
    @Nullable String ticketReference,
    @Schema(types = {"string", "null"})
    @Nullable String ticketStatus) {
}