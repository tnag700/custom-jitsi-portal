package com.acme.jitsi.domains.admin.service;

public final class AdminIncidentsInvalidRequestException extends RuntimeException {

  public AdminIncidentsInvalidRequestException(String message) {
    super(message);
  }

  public AdminIncidentsInvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}