package com.acme.jitsi.security;

public class JwtStartupValidationException extends RuntimeException {

  private final JwtStartupValidationErrorCode errorCode;

  public JwtStartupValidationException(JwtStartupValidationErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode.name();
  }

  JwtStartupValidationErrorCode errorCodeEnum() {
    return errorCode;
  }
}
