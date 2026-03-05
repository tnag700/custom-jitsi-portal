package com.acme.jitsi.domains.meetings.service;

import org.springframework.http.HttpStatus;

public class MeetingTokenException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;

  public MeetingTokenException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  public MeetingTokenException(HttpStatus status, String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.errorCode = errorCode;
  }

  public HttpStatus status() {
    return status;
  }

  public String errorCode() {
    return errorCode;
  }
}
