package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository {
  Meeting save(Meeting meeting);

  Optional<Meeting> findById(String meetingId);

  boolean existsById(String meetingId);

  List<Meeting> findByRoomId(String roomId, int page, int size);

  boolean existsActiveOrFutureMeetings(String roomId, Instant activeThreshold, Instant now);

  long countByRoomId(String roomId);
}