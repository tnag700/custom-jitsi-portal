package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import org.springframework.http.HttpStatus;

final class RetryableInviteUsageException extends MeetingTokenException {

  RetryableInviteUsageException(HttpStatus status, String errorCode, String message) {
    super(status, errorCode, message);
  }

  RetryableInviteUsageException(HttpStatus status, String errorCode, String message, Throwable cause) {
    super(status, errorCode, message, cause);
  }
}
