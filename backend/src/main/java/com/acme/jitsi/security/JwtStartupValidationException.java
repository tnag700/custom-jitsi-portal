package com.acme.jitsi.security;

class JwtStartupValidationException extends RuntimeException {

  private final JwtStartupValidationErrorCode errorCode;

  JwtStartupValidationException(JwtStartupValidationErrorCode errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  String errorCode() {
    return errorCode.name();
  }

  JwtStartupValidationErrorCode errorCodeEnum() {
    return errorCode;
  }
}