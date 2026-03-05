package com.acme.jitsi.domains.meetings.infrastructure;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MeetingInviteJpaRepository extends JpaRepository<MeetingInviteEntity, String> {
  Page<MeetingInviteEntity> findByMeetingIdOrderByCreatedAtDesc(String meetingId, Pageable pageable);

  long countByMeetingId(String meetingId);

  boolean existsByMeetingId(String meetingId);

  Optional<MeetingInviteEntity> findByToken(String token);

  @Query("""
      select mi from MeetingInviteEntity mi
      where mi.meetingId = :meetingId
        and mi.recipientEmail = :recipientEmail
        and mi.revokedAt is null
        and mi.usedCount < mi.maxUses
        and (mi.expiresAt is null or mi.expiresAt > :now)
      order by mi.createdAt desc
      """)
  Optional<MeetingInviteEntity> findFirstActiveByMeetingIdAndRecipientEmail(
      @Param("meetingId") String meetingId,
      @Param("recipientEmail") String recipientEmail,
      @Param("now") Instant now);

  @Query("""
      select mi from MeetingInviteEntity mi
      where mi.meetingId = :meetingId
        and mi.recipientUserId = :recipientUserId
        and mi.revokedAt is null
        and mi.usedCount < mi.maxUses
        and (mi.expiresAt is null or mi.expiresAt > :now)
      order by mi.createdAt desc
      """)
  Optional<MeetingInviteEntity> findFirstActiveByMeetingIdAndRecipientUserId(
      @Param("meetingId") String meetingId,
      @Param("recipientUserId") String recipientUserId,
      @Param("now") Instant now);
}
