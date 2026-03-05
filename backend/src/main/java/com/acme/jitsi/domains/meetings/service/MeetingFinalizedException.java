package com.acme.jitsi.domains.meetings.service;

public class MeetingFinalizedException extends RuntimeException {
  public MeetingFinalizedException(String meetingId) {
    super("Изменение встречи '" + meetingId + "' запрещено: встреча завершена или отменена");
  }
}