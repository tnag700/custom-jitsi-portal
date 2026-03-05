package com.acme.jitsi.domains.meetings.service;

public class InvalidMeetingDataException extends RuntimeException {

  public InvalidMeetingDataException(String message) {
    super(message);
  }
}
