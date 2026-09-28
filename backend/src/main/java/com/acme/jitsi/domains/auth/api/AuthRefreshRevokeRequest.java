package com.acme.jitsi.domains.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record AuthRefreshRevokeRequest(
    @NotBlank(message = "tokenId обязателен")
    @Pattern(regexp = "[A-Za-z0-9._:-]{1,255}", message = "Некорректный tokenId") String tokenId) {
}
