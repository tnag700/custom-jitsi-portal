package com.acme.jitsi.domains.meetings.api;

import java.util.List;

record PagedMeetingResponse(
    List<MeetingResponse> content,
    int page,
    int pageSize,
    long totalElements,
    int totalPages) {
}