package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaMeetingInviteRepository implements MeetingInviteRepository {

  private final MeetingInviteJpaRepository jpaRepository;

  JpaMeetingInviteRepository(MeetingInviteJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public MeetingInvite save(MeetingInvite invite) {
    MeetingInviteEntity entity = jpaRepository.findById(invite.id())
        .map(existing -> {
          existing.updateFrom(invite);
          return existing;
        })
        .orElseGet(() -> new MeetingInviteEntity(invite));

    return jpaRepository.save(entity).toDomain();
  }

  @Override
  public List<MeetingInvite> saveAll(List<MeetingInvite> invites) {
    List<MeetingInviteEntity> entities = invites.stream()
        .map(invite -> jpaRepository.findById(invite.id())
            .map(existing -> {
              existing.updateFrom(invite);
              return existing;
            })
            .orElseGet(() -> new MeetingInviteEntity(invite)))
        .toList();

    return jpaRepository.saveAll(entities).stream()
        .map(MeetingInviteEntity::toDomain)
        .toList();
  }

  @Override
  public Optional<MeetingInvite> findById(String id) {
    return jpaRepository.findById(id).map(MeetingInviteEntity::toDomain);
  }

  @Override
  public Optional<MeetingInvite> findByToken(String token) {
    return jpaRepository.findByToken(token).map(MeetingInviteEntity::toDomain);
  }

  @Override
  public List<MeetingInvite> findByMeetingId(String meetingId, int page, int size) {
    Page<MeetingInviteEntity> entityPage = jpaRepository.findByMeetingIdOrderByCreatedAtDesc(
        meetingId, PageRequest.of(page, size));
    return entityPage.stream().map(MeetingInviteEntity::toDomain).toList();
  }

  @Override
  public Optional<MeetingInvite> findActiveByMeetingIdAndRecipientEmail(
      String meetingId,
      String recipientEmail,
      Instant now) {
    return jpaRepository
        .findFirstActiveByMeetingIdAndRecipientEmail(meetingId, recipientEmail, now)
        .map(MeetingInviteEntity::toDomain);
  }

  @Override
  public Optional<MeetingInvite> findActiveByMeetingIdAndRecipientUserId(
      String meetingId,
      String recipientUserId,
      Instant now) {
    return jpaRepository
        .findFirstActiveByMeetingIdAndRecipientUserId(meetingId, recipientUserId, now)
        .map(MeetingInviteEntity::toDomain);
  }

  @Override
  public long countByMeetingId(String meetingId) {
    return jpaRepository.countByMeetingId(meetingId);
  }

  @Override
  public boolean existsByMeetingId(String meetingId) {
    return jpaRepository.existsByMeetingId(meetingId);
  }
}
