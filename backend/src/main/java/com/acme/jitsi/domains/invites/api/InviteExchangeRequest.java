package com.acme.jitsi.domains.invites.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.acme.jitsi.shared.validation.TextInputNormalizer;

record InviteExchangeRequest(
    @NotBlank String inviteToken,
        @Size(min = 2, max = 80, message = "displayName must be between 2 and 80 characters")
        @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "displayName must not contain control characters")
        String displayName) {

    InviteExchangeRequest {
        inviteToken = TextInputNormalizer.normalizeRequired(inviteToken);
        displayName = TextInputNormalizer.normalizeNullable(displayName);
    }
}