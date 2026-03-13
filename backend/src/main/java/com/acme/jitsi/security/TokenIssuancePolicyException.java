package com.acme.jitsi.security;

import org.springframework.http.HttpStatus;

public class TokenIssuancePolicyException extends RuntimeException {

  private final HttpStatus status;
  private final String errorCode;

  public TokenIssuancePolicyException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  public TokenIssuancePolicyException(HttpStatus status, String errorCode, String message, Throwable cause) {
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