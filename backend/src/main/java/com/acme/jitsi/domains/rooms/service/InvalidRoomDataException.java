package com.acme.jitsi.domains.rooms.service;

public class InvalidRoomDataException extends RuntimeException {
  public InvalidRoomDataException(String message) {
    super(message);
  }
}
