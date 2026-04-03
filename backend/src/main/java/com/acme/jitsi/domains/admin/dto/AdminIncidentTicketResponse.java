package com.acme.jitsi.domains.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record AdminIncidentTicketResponse(
    boolean available,
    boolean created,
    @Schema(types = {"string", "null"})
    @Nullable String ticketKey,
    @Schema(types = {"string", "null"})
    @Nullable String ticketUrl,
    @Schema(types = {"string", "null"})
    @Nullable String summary,
    @Schema(types = {"string", "null"})
    @Nullable String message) {
}