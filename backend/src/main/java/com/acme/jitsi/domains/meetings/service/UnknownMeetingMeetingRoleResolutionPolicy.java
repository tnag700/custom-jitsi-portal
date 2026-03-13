package com.acme.jitsi.domains.meetings.service;

import com.acme.jitsi.shared.ErrorCode;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Order(200)
class UnknownMeetingMeetingRoleResolutionPolicy implements MeetingRoleResolutionPolicy {

  private final MeetingRepository meetingRepository;

  UnknownMeetingMeetingRoleResolutionPolicy(MeetingRepository meetingRepository) {
    this.meetingRepository = meetingRepository;
  }

  @Override
  public Optional<MeetingRole> resolve(MeetingRoleResolutionContext context) {
    if (!context.properties().knownMeetingIds().isEmpty()
        && !context.properties().knownMeetingIds().contains(context.meetingId())
        && !meetingRepository.existsById(context.meetingId())) {
      throw new MeetingTokenException(HttpStatus.NOT_FOUND, ErrorCode.MEETING_NOT_FOUND.code(), "Встреча не найдена.");
    }
    return Optional.empty();
  }
}