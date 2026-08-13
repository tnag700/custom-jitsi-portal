package com.acme.jitsi.domains.invites.api;

import com.acme.jitsi.shared.validation.TextInputNormalizer;
import jakarta.validation.constraints.NotBlank;

record InviteValidationRequest(@NotBlank String inviteToken) {

  InviteValidationRequest {
    inviteToken = TextInputNormalizer.normalizeRequired(inviteToken);
  }
}
