package com.acme.jitsi.domains.meetings.usecase;

final class RetryableConsumeInviteContentionException extends RuntimeException {

  private final String token;

  RetryableConsumeInviteContentionException(String token, Throwable cause) {
    super("Retryable consume-invite contention: " + token, cause);
    this.token = token;
  }

  String token() {
    return token;
  }
}