package com.acme.jitsi.domains.rooms.service;

public class RoomNameConflictException extends RuntimeException {
  public RoomNameConflictException(String name, String tenantId) {
    super("Room with name '" + name + "' already exists in tenant '" + tenantId + "'");
  }
}
