package com.acme.jitsi.domains.rooms.service;

public class RoomNotFoundException extends RuntimeException {
  public RoomNotFoundException(String roomId) {
    super("Room '" + roomId + "' not found");
  }
}
