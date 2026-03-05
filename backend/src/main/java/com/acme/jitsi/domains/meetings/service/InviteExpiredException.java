package com.acme.jitsi.domains.meetings.service;

public class InviteExpiredException extends RuntimeException {

  private final String token;

  public InviteExpiredException(String token) {
    super("Invite expired: " + token);
    this.token = token;
  }

  public String getToken() {
    return token;
  }
}
