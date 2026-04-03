package com.acme.jitsi.domains.admin.service;

public final class AdminDashboardInvalidRequestException extends RuntimeException {

  public AdminDashboardInvalidRequestException(String message) {
    super(message);
  }

  public AdminDashboardInvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}