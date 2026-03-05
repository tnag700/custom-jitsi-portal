package com.acme.jitsi.domains.meetings.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingInviteService {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingRepository meetingRepository;

  public MeetingInviteService(
      MeetingInviteRepository inviteRepository,
      MeetingRepository meetingRepository) {
    this.inviteRepository = inviteRepository;
    this.meetingRepository = meetingRepository;
  }

  public List<MeetingInvite> listByMeeting(String meetingId, int page, int size) {
    // Validate meeting exists
    meetingRepository.findById(meetingId)
        .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    
    return inviteRepository.findByMeetingId(meetingId, page, size);
  }

  public long countByMeeting(String meetingId) {
    return inviteRepository.countByMeetingId(meetingId);
  }

  public Optional<MeetingInvite> findByToken(String token) {
    return inviteRepository.findByToken(token);
  }

  @Transactional
  public void rollbackConsume(String token) {
    inviteRepository.findByToken(token).ifPresent(invite -> {
      if (invite.usedCount() > 0) {
        inviteRepository.save(invite.withUsedCount(invite.usedCount() - 1));
      }
    });
  }

}
