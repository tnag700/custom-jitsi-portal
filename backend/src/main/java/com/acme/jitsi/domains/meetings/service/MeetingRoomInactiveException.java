package com.acme.jitsi.domains.meetings.service;

public class MeetingRoomInactiveException extends RuntimeException {
  public MeetingRoomInactiveException(String roomId) {
    super("Создание встречи в неактивной комнате запрещено: " + roomId);
  }
}