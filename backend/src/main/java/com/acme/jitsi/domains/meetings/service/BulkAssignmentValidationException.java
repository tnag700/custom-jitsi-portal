package com.acme.jitsi.domains.meetings.service;

public class BulkAssignmentValidationException extends RuntimeException {
  private final int rowIndex;
  private final String subjectId;
  private final String errorCode;

  public BulkAssignmentValidationException(String message, int rowIndex, String subjectId, String errorCode, Throwable cause) {
    super(message, cause);
    this.rowIndex = rowIndex;
    this.subjectId = subjectId;
    this.errorCode = errorCode;
  }

  public int rowIndex() {
    return rowIndex;
  }

  public String subjectId() {
    return subjectId;
  }

  public String errorCode() {
    return errorCode;
  }
}
