package com.acme.jitsi.domains.meetings.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class MeetingRoleConflictException extends RuntimeException {

  private final String errorCode;

  public MeetingRoleConflictException(String message) {
    super(message);
    this.errorCode = "ROLE_CONFLICT";
  }

  public MeetingRoleConflictException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  public MeetingRoleConflictException(String message, String errorCode, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
