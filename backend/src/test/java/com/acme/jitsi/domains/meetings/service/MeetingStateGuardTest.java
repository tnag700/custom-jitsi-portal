package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.TestFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MeetingStateGuardTest {

  @Mock
  private MeetingRepository meetingRepository;

  private MeetingStateGuard meetingStateGuard;

  @BeforeEach
  void setUp() {
    Clock fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    MeetingTokenProperties tokenProperties = new MeetingTokenProperties();
    tokenProperties.setKnownMeetingIds(List.of());
    meetingStateGuard = new MeetingStateGuard(meetingRepository, fixedClock, tokenProperties);
  }

  @Test
  void assertJoinAllowed_meetingNotFound_throwsMeetingNotFoundTokenException() {
    when(meetingRepository.findById("missing-meeting")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> meetingStateGuard.assertJoinAllowed("missing-meeting"))
        .isInstanceOfSatisfying(MeetingTokenException.class, ex -> {
          org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
          org.assertj.core.api.Assertions.assertThat(ex.errorCode()).isEqualTo(ErrorCode.MEETING_NOT_FOUND.code());
        });
  }

  @Test
  void assertGuestJoinAllowed_rejectsMeetingWithGuestAccessDisabled() {
    Meeting meeting = Meeting.builder()
        .meetingId("meeting-private")
        .roomId("room-1")
        .title("Private meeting")
        .description("Guests disabled")
        .meetingType("scheduled")
        .configSetId("config-1")
        .status(MeetingStatus.SCHEDULED)
        .startsAt(Instant.parse("2025-12-31T23:00:00Z"))
        .endsAt(Instant.parse("2026-01-01T01:00:00Z"))
        .allowGuests(false)
        .recordingEnabled(false)
        .createdAt(Instant.parse("2025-12-01T00:00:00Z"))
        .updatedAt(Instant.parse("2025-12-01T00:00:00Z"))
        .build();

    assertThatThrownBy(() -> meetingStateGuard.assertGuestJoinAllowed(meeting))
        .isInstanceOfSatisfying(MeetingTokenException.class, ex -> {
          org.assertj.core.api.Assertions.assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
          org.assertj.core.api.Assertions.assertThat(ex.errorCode())
              .isEqualTo(ErrorCode.GUEST_ACCESS_DISABLED.code());
        });
  }

  @Test
  void assertGuestJoinAllowed_acceptsActiveMeetingWithGuestAccessEnabled() {
    Meeting meeting = TestFixtures.mockMeeting("meeting-public", "room-1");

    org.assertj.core.api.Assertions.assertThatCode(
            () -> meetingStateGuard.assertGuestJoinAllowed(meeting))
        .doesNotThrowAnyException();
  }
}
