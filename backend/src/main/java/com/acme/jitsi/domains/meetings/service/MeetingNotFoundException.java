package com.acme.jitsi.domains.meetings.service;

public class MeetingNotFoundException extends RuntimeException {
  public MeetingNotFoundException(String meetingId) {
    super("Встреча с ID '" + meetingId + "' не найдена");
  }
}