package com.acme.jitsi.domains.auth.service;

import org.springframework.http.HttpStatus;

final class RetryableRefreshTokenException extends AuthTokenException {

  RetryableRefreshTokenException(HttpStatus status, String errorCode, String message) {
    super(status, errorCode, message);
  }

  RetryableRefreshTokenException(HttpStatus status, String errorCode, String message, Throwable cause) {
    super(status, errorCode, message, cause);
  }
}