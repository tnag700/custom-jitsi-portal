package com.acme.jitsi.domains.rooms.service;

import java.util.List;

public class RoomService {

  private final RoomRepository roomRepository;

  public RoomService(
      RoomRepository roomRepository) {
    this.roomRepository = roomRepository;
  }

  public Room getRoom(String roomId) {
    return roomRepository.findById(roomId)
        .orElseThrow(() -> new RoomNotFoundException(roomId));
  }

  public List<Room> listRooms(String tenantId, int page, int size) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new InvalidRoomDataException("Tenant ID is required");
    }
    if (page < 0) {
      throw new InvalidRoomDataException("Page must be greater than or equal to 0");
    }
    if (size <= 0) {
      throw new InvalidRoomDataException("Size must be greater than 0");
    }
    return roomRepository.findByTenantId(tenantId, page, size);
  }

  public long countRooms(String tenantId) {
    return roomRepository.countByTenantId(tenantId);
  }
}
