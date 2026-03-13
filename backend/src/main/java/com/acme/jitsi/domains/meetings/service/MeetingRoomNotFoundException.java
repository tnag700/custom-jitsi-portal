package com.acme.jitsi.domains.meetings.service;

public class MeetingRoomNotFoundException extends RuntimeException {
  public MeetingRoomNotFoundException(String roomId) {
    super("Room '" + roomId + "' not found");
  }
}