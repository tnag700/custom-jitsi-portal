package com.acme.jitsi.domains.rooms.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomRepository {
  Room save(Room room);
  Optional<Room> findById(String roomId);
  Optional<Room> findByIdForUpdate(String roomId);
  List<Room> findByIds(Set<String> roomIds);
  List<Room> findByTenantId(String tenantId, int page, int size);
  long countByTenantId(String tenantId);
  boolean existsByNameAndTenantId(String name, String tenantId);
  boolean existsByNameAndTenantIdAndRoomIdNot(String name, String tenantId, String excludeRoomId);
  void deleteById(String roomId);
}
