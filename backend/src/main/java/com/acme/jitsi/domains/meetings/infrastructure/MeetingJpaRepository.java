package com.acme.jitsi.domains.meetings.infrastructure;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MeetingJpaRepository extends JpaRepository<MeetingEntity, String> {
  Page<MeetingEntity> findByRoomIdOrderByCreatedAtDesc(String roomId, Pageable pageable);

  @Query("""
      SELECT COUNT(m) > 0 FROM MeetingEntity m
      WHERE m.roomId = :roomId
        AND m.status <> 'CANCELED'
        AND (
          (m.startsAt IS NOT NULL AND m.endsAt IS NOT NULL
            AND m.startsAt <= :now AND m.endsAt > :now)
          OR (m.startsAt IS NOT NULL AND m.startsAt > :activeThreshold
            AND (m.endsAt IS NULL OR m.endsAt > :now))
        )
      """)
  boolean existsActiveOrFutureMeetings(
      @Param("roomId") String roomId,
      @Param("activeThreshold") Instant activeThreshold,
      @Param("now") Instant now);

  long countByRoomId(String roomId);
}