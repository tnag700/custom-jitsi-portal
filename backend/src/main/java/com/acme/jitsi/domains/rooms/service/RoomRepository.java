package com.acme.jitsi.domains.rooms.service;

import java.util.List;
import java.util.Optional;

public interface RoomRepository {
  Room save(Room room);
  Optional<Room> findById(String roomId);
  List<Room> findByTenantId(String tenantId, int page, int size);
  long countByTenantId(String tenantId);
  boolean existsByNameAndTenantId(String name, String tenantId);
  boolean existsByNameAndTenantIdAndRoomIdNot(String name, String tenantId, String excludeRoomId);
  void deleteById(String roomId);
}
