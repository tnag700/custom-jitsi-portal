package com.acme.jitsi.domains.meetings.service;

public class InviteNotFoundException extends RuntimeException {

  private final String inviteId;

  public InviteNotFoundException(String inviteId) {
    super("Invite not found: " + inviteId);
    this.inviteId = inviteId;
  }

  public String getInviteId() {
    return inviteId;
  }
}
