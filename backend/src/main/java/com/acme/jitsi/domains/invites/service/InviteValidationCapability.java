package com.acme.jitsi.domains.invites.service;

public interface InviteValidationCapability {

  InviteValidationResult validate(String inviteToken);
}