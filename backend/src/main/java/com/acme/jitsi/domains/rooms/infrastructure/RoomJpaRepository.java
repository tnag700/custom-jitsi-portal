package com.acme.jitsi.domains.rooms.infrastructure;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface RoomJpaRepository extends JpaRepository<RoomEntity, String> {

  Page<RoomEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

  long countByTenantId(String tenantId);

  boolean existsByNameAndTenantId(String name, String tenantId);

  boolean existsByNameAndTenantIdAndRoomIdNot(String name, String tenantId, String roomId);
}
