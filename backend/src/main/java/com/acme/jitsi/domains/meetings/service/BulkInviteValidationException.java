package com.acme.jitsi.domains.meetings.service;

import java.util.List;

public class BulkInviteValidationException extends RuntimeException {

  private final List<BulkInviteError> errors;
  private final BulkInviteResult result;

  public BulkInviteValidationException(String message, List<BulkInviteError> errors, BulkInviteResult result) {
    super(message);
    this.errors = List.copyOf(errors);
    this.result = result;
  }

  public List<BulkInviteError> errors() {
    return errors;
  }

  public BulkInviteResult result() {
    return result;
  }
}
