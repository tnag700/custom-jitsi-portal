package com.acme.jitsi.domains.meetings.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MeetingInvalidRoleException extends RuntimeException {

  private final String errorCode;

  public MeetingInvalidRoleException(String message) {
    super(message);
    this.errorCode = "INVALID_ROLE";
  }

  public MeetingInvalidRoleException(String message, String errorCode) {
    super(message);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
