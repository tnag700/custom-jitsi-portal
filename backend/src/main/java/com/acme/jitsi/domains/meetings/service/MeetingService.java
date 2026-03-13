package com.acme.jitsi.domains.meetings.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MeetingService {

  private final MeetingRepository meetingRepository;

  public MeetingService(MeetingRepository meetingRepository) {
    this.meetingRepository = meetingRepository;
  }

  public Meeting getMeeting(String meetingId) {
    return meetingRepository.findById(meetingId)
        .orElseThrow(() -> new MeetingNotFoundException(meetingId));
  }

  public List<Meeting> listMeetings(String roomId, int page, int size) {
    if (page < 0) {
      throw new InvalidMeetingDataException("Page must be greater than or equal to 0");
    }
    if (size <= 0) {
      throw new InvalidMeetingDataException("Size must be greater than 0");
    }

    return meetingRepository.findByRoomId(roomId, page, size);
  }

  public long countMeetings(String roomId) {
    return meetingRepository.countByRoomId(roomId);
  }
}