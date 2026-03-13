package com.acme.jitsi.domains.auth.service;

import org.springframework.http.HttpStatus;

public class AuthTokenException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;

  public AuthTokenException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  public AuthTokenException(HttpStatus status, String errorCode, String message, Throwable cause) {
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