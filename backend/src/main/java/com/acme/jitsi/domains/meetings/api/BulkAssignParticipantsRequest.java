package com.acme.jitsi.domains.meetings.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

record BulkAssignParticipantsRequest(
    @NotEmpty(message = "Participants list cannot be empty")
    @Valid
    List<ParticipantAssignment> participants,
    
    @Pattern(regexp = "^(host|moderator|participant)$", message = "Default role must be host, moderator, or participant")
    String defaultRole) {
  
  record ParticipantAssignment(
      @NotBlank(message = "Subject ID is required")
      String subjectId,
      
      @Pattern(regexp = "^(host|moderator|participant)?$", message = "Role must be host, moderator, or participant")
      String role) {
  }
}
