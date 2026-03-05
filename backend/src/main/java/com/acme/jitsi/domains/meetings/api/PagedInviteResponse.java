package com.acme.jitsi.domains.meetings.api;

import java.util.List;

public record PagedInviteResponse(
    List<InviteResponse> content,
    int page,
    int pageSize,
    long totalElements,
    int totalPages
) {}
