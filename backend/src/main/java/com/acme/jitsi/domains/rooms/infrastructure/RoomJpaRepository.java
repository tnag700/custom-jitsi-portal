package com.acme.jitsi.domains.rooms.infrastructure;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RoomJpaRepository extends JpaRepository<RoomEntity, String> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM RoomEntity r WHERE r.roomId = :roomId")
  Optional<RoomEntity> findByIdForUpdate(@Param("roomId") String roomId);

  Page<RoomEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

  long countByTenantId(String tenantId);

  boolean existsByNameAndTenantId(String name, String tenantId);

  boolean existsByNameAndTenantIdAndRoomIdNot(String name, String tenantId, String roomId);
}
