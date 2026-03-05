package com.acme.jitsi.domains.rooms.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultActiveMeetingsCheckerTest {

  @Mock
  private MeetingRepository meetingRepository;

  @Test
  void hasActiveOrFutureMeetings_withMoreThanHundredMeetings_usesExistsQuery() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    DefaultActiveMeetingsChecker checker = new DefaultActiveMeetingsChecker(meetingRepository, fixedClock, 120);

    when(meetingRepository.existsActiveOrFutureMeetings(
        "room-1",
        Instant.parse("2026-01-01T08:00:00Z"),
        Instant.parse("2026-01-01T10:00:00Z")))
        .thenReturn(true);

    boolean result = checker.hasActiveOrFutureMeetings("room-1");

    assertThat(result).isTrue();
    verify(meetingRepository).existsActiveOrFutureMeetings(
        "room-1",
        Instant.parse("2026-01-01T08:00:00Z"),
        Instant.parse("2026-01-01T10:00:00Z"));
    verify(meetingRepository, never()).findByRoomId("room-1", 0, 100);
  }
}
