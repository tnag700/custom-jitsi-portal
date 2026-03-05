package com.acme.jitsi.domains.auth.api;

import java.util.Collection;

public record SafeUserProfileResponse(
    String id,
    String displayName,
    String email,
    String tenant,
    Collection<String> claims) {
}
