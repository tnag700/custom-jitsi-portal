package com.acme.jitsi.domains.rooms.api;

import jakarta.validation.constraints.Size;
import com.acme.jitsi.shared.validation.TextInputNormalizer;

record UpdateRoomRequest(
    @Size(min = 1, max = 255, message = "Room name must be between 1 and 255 characters")
    String name,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description,
    String configSetId) {

    UpdateRoomRequest {
        name = TextInputNormalizer.normalizeNullable(name);
        description = TextInputNormalizer.normalizeOptional(description);
        configSetId = TextInputNormalizer.normalizeNullable(configSetId);
    }
}
