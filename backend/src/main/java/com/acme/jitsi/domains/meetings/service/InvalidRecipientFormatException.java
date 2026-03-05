package com.acme.jitsi.domains.meetings.service;

public class InvalidRecipientFormatException extends RuntimeException {

  private final int rowIndex;
  private final String recipient;

  public InvalidRecipientFormatException(int rowIndex, String recipient, String message) {
    super(message);
    this.rowIndex = rowIndex;
    this.recipient = recipient;
  }

  public InvalidRecipientFormatException(int rowIndex, String recipient, String message, Throwable cause) {
    super(message, cause);
    this.rowIndex = rowIndex;
    this.recipient = recipient;
  }

  public int rowIndex() {
    return rowIndex;
  }

  public String recipient() {
    return recipient;
  }
}
