package com.acme.jitsi.domains.rooms.api;

import jakarta.validation.constraints.Size;

record UpdateRoomRequest(
    @Size(min = 1, max = 255, message = "Room name must be between 1 and 255 characters")
    String name,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    String description,
    String configSetId) {
}
