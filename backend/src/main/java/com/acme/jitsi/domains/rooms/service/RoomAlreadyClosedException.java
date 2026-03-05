package com.acme.jitsi.domains.rooms.service;

public class RoomAlreadyClosedException extends RuntimeException {
  public RoomAlreadyClosedException(String roomId) {
    super("Room '" + roomId + "' is already closed");
  }
}
