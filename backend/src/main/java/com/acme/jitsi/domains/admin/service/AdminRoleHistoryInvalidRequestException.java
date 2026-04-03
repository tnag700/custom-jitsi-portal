package com.acme.jitsi.domains.admin.service;

public final class AdminRoleHistoryInvalidRequestException extends RuntimeException {

  public AdminRoleHistoryInvalidRequestException(String message) {
    super(message);
  }

  public AdminRoleHistoryInvalidRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}