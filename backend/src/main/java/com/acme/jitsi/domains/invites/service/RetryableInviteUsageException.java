package com.acme.jitsi.domains.invites.service;

import org.springframework.http.HttpStatus;

final class RetryableInviteUsageException extends InviteExchangeException {

  RetryableInviteUsageException(HttpStatus status, String errorCode, String message) {
    super(status, errorCode, message);
  }

  RetryableInviteUsageException(HttpStatus status, String errorCode, String message, Throwable cause) {
    super(status, errorCode, message, cause);
  }
}
