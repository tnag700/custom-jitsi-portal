package com.acme.jitsi.domains.meetings.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record AssignParticipantRequest(
    @NotBlank(message = "Subject ID is required")
    String subjectId,
    
    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(host|moderator|participant)$", message = "Role must be host, moderator, or participant")
    String role) {
}
