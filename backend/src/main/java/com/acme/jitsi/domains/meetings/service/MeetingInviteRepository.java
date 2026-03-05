package com.acme.jitsi.domains.meetings.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MeetingInviteRepository {

  MeetingInvite save(MeetingInvite invite);
  
  List<MeetingInvite> saveAll(List<MeetingInvite> invites);

  Optional<MeetingInvite> findById(String id);

  Optional<MeetingInvite> findByToken(String token);

  List<MeetingInvite> findByMeetingId(String meetingId, int page, int size);

  Optional<MeetingInvite> findActiveByMeetingIdAndRecipientEmail(String meetingId, String recipientEmail, Instant now);

  Optional<MeetingInvite> findActiveByMeetingIdAndRecipientUserId(String meetingId, String recipientUserId, Instant now);

  long countByMeetingId(String meetingId);

  boolean existsByMeetingId(String meetingId);
}
