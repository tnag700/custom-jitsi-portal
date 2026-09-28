package com.acme.jitsi.domains.meetings.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

interface MeetingJpaRepository extends JpaRepository<MeetingEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT m FROM MeetingEntity m WHERE m.meetingId = :meetingId")
  Optional<MeetingEntity> findByIdForUpdate(@Param("meetingId") String meetingId);

  @Query("""
      SELECT m FROM MeetingEntity m
      WHERE m.status = 'SCHEDULED' AND m.endsAt >= :now
        AND EXISTS (SELECT a FROM MeetingParticipantAssignmentEntity a
                    WHERE a.meetingId = m.meetingId AND a.subjectId = :subjectId)
      ORDER BY m.startsAt, m.meetingId
      """)
  List<MeetingEntity> findUpcomingBySubjectId(@Param("subjectId") String subjectId, @Param("now") Instant now);

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
