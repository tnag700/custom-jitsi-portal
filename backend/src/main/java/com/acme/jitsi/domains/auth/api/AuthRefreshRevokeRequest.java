package com.acme.jitsi.domains.auth.api;

import jakarta.validation.constraints.NotBlank;

record AuthRefreshRevokeRequest(@NotBlank(message = "tokenId обязателен") String tokenId) {
}
