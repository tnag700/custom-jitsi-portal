package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.rooms.service.Room;
import com.acme.jitsi.domains.rooms.service.RoomRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaRoomRepository implements RoomRepository {

  private final RoomJpaRepository jpaRepository;

  JpaRoomRepository(RoomJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Room save(Room room) {
    RoomEntity entity = jpaRepository.findById(room.roomId())
        .map(existing -> {
          existing.updateFrom(room);
          return existing;
        })
        .orElseGet(() -> new RoomEntity(room));

    RoomEntity saved = jpaRepository.save(entity);
    return saved.toDomain();
  }

  @Override
  public Optional<Room> findById(String roomId) {
    return jpaRepository.findById(roomId)
        .map(RoomEntity::toDomain);
  }

  @Override
  public List<Room> findByTenantId(String tenantId, int page, int size) {
    Page<RoomEntity> entityPage = jpaRepository.findByTenantIdOrderByCreatedAtDesc(
        tenantId, PageRequest.of(page, size));
    return entityPage.stream()
        .map(RoomEntity::toDomain)
        .toList();
  }

  @Override
  public long countByTenantId(String tenantId) {
    return jpaRepository.countByTenantId(tenantId);
  }

  @Override
  public boolean existsByNameAndTenantId(String name, String tenantId) {
    return jpaRepository.existsByNameAndTenantId(name, tenantId);
  }

  @Override
  public boolean existsByNameAndTenantIdAndRoomIdNot(String name, String tenantId, String excludeRoomId) {
    return jpaRepository.existsByNameAndTenantIdAndRoomIdNot(name, tenantId, excludeRoomId);
  }

  @Override
  public void deleteById(String roomId) {
    jpaRepository.deleteById(roomId);
  }
}
