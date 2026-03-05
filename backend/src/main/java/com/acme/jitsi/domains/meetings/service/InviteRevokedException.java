package com.acme.jitsi.domains.meetings.service;

public class InviteRevokedException extends RuntimeException {

  private final String token;

  public InviteRevokedException(String token) {
    super("Invite revoked: " + token);
    this.token = token;
  }

  public String getToken() {
    return token;
  }
}
