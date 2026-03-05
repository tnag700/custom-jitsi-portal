package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.rooms.service.ActiveMeetingsChecker;
import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DefaultActiveMeetingsChecker implements ActiveMeetingsChecker {

  private final MeetingRepository meetingRepository;
  private final Clock clock;
  private final int activeWindowMinutes;

  DefaultActiveMeetingsChecker(
      MeetingRepository meetingRepository,
      Clock clock,
      @Value("${app.rooms.active-meeting-window-minutes:120}") int activeWindowMinutes) {
    this.meetingRepository = meetingRepository;
    this.clock = clock;
    this.activeWindowMinutes = activeWindowMinutes;
  }

  @Override
  public boolean hasActiveOrFutureMeetings(String roomId) {
    Instant now = Instant.now(clock);
    Instant activeThreshold = now.minusSeconds(activeWindowMinutes * 60L);
    return meetingRepository.existsActiveOrFutureMeetings(roomId, activeThreshold, now);
  }
}
