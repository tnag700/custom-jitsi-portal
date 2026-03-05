package com.acme.jitsi.domains.meetings.service;

public class InvalidMeetingScheduleException extends RuntimeException {
  public InvalidMeetingScheduleException() {
    super("Время начала должно быть раньше времени окончания");
  }
}