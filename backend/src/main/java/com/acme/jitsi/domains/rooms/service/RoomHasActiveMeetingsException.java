package com.acme.jitsi.domains.rooms.service;

public class RoomHasActiveMeetingsException extends RuntimeException {
  public RoomHasActiveMeetingsException(String roomId) {
    super("Room '" + roomId + "' has active or future meetings");
  }
}
