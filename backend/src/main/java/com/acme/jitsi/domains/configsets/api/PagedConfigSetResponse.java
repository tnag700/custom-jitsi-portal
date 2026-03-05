package com.acme.jitsi.domains.configsets.api;

import java.util.List;

record PagedConfigSetResponse(
    List<ConfigSetResponse> content,
    int page,
    int pageSize,
    long totalElements,
    int totalPages) {
}