package com.acme.jitsi.domains.auth.api;

import jakarta.validation.constraints.NotBlank;

record AuthRefreshRequest(@NotBlank(message = "refreshToken обязателен") String refreshToken) {
}
