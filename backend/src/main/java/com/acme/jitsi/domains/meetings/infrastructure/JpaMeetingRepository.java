package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaMeetingRepository implements MeetingRepository {

  private final MeetingJpaRepository jpaRepository;

  JpaMeetingRepository(MeetingJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public Meeting save(Meeting meeting) {
    return jpaRepository.save(new MeetingEntity(meeting)).toDomain();
  }

  @Override
  public Optional<Meeting> findById(String meetingId) {
    return jpaRepository.findById(meetingId).map(MeetingEntity::toDomain);
  }

  @Override
  public boolean existsById(String meetingId) {
    return jpaRepository.existsById(meetingId);
  }

  @Override
  public List<Meeting> findByRoomId(String roomId, int page, int size) {
    Page<MeetingEntity> entityPage = jpaRepository.findByRoomIdOrderByCreatedAtDesc(
        roomId, PageRequest.of(page, size));
    return entityPage.stream().map(MeetingEntity::toDomain).toList();
  }

  @Override
  public boolean existsActiveOrFutureMeetings(String roomId, Instant activeThreshold, Instant now) {
    return jpaRepository.existsActiveOrFutureMeetings(roomId, activeThreshold, now);
  }

  @Override
  public long countByRoomId(String roomId) {
    return jpaRepository.countByRoomId(roomId);
  }
}