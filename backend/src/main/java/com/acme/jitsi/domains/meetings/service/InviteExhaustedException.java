package com.acme.jitsi.domains.meetings.service;

public class InviteExhaustedException extends RuntimeException {

  private final String token;

  public InviteExhaustedException(String token) {
    super("Invite exhausted: " + token);
    this.token = token;
  }

  public InviteExhaustedException(String token, Throwable cause) {
    super("Invite exhausted: " + token, cause);
    this.token = token;
  }

  public String getToken() {
    return token;
  }
}
