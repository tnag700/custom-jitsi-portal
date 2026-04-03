package com.acme.jitsi.domains.meetings.service;

public class MeetingRoomNotFoundException extends RuntimeException {
  public MeetingRoomNotFoundException(String roomId) {
    this(roomId, null);
  }

  public MeetingRoomNotFoundException(String roomId, Throwable cause) {
    super("Room '" + roomId + "' not found", cause);
  }
}