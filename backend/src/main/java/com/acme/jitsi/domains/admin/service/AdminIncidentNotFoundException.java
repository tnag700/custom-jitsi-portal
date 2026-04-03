package com.acme.jitsi.domains.admin.service;

public final class AdminIncidentNotFoundException extends RuntimeException {

  public AdminIncidentNotFoundException(String message) {
    super(message);
  }
}