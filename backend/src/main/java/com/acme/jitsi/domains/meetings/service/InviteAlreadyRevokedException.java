package com.acme.jitsi.domains.meetings.service;

public class InviteAlreadyRevokedException extends RuntimeException {

  private final String inviteId;

  public InviteAlreadyRevokedException(String inviteId) {
    super("Invite already revoked: " + inviteId);
    this.inviteId = inviteId;
  }

  public String getInviteId() {
    return inviteId;
  }
}
