package com.acme.jitsi.domains.profiles.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record UpsertProfileRequest(
    @NotBlank @Size(min = 2, max = 500) String fullName,
    @NotBlank @Size(min = 2, max = 500) String organization,
    @NotBlank @Size(min = 2, max = 500) String position) {
}
