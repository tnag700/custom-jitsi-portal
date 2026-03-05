package com.acme.jitsi.domains.rooms.api;

import java.util.List;

record PagedRoomResponse(
    List<RoomResponse> content,
    int page,
    int pageSize,
    long totalElements,
    int totalPages) {
}
