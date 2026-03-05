package com.acme.jitsi.domains.invites.api;

import jakarta.validation.constraints.NotBlank;

record InviteExchangeRequest(
    @NotBlank String inviteToken,
    String displayName) {
}