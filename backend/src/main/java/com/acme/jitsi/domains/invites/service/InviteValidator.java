package com.acme.jitsi.domains.invites.service;

interface InviteValidator {

  void validate(InviteValidationContext context);

  default boolean requiresResolvedInvite() {
    return true;
  }

  default boolean loadsResolvedInvite() {
    return false;
  }

  default boolean isTerminalValidator() {
    return false;
  }
}