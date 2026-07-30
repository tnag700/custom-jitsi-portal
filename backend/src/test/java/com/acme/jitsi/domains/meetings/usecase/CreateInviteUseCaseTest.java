package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.TestFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CreateInviteUseCaseTest {

  @Mock
  private MeetingInviteRepository inviteRepository;
  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private MeetingStateGuard meetingStateGuard;

  private CreateInviteUseCase useCase;
  private Clock clock;

  @BeforeEach
  void setUp() {
    clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    useCase = new CreateInviteUseCase(
        inviteRepository,
        meetingRepository,
        meetingStateGuard,
        eventPublisher,
        new SecureInviteTokenGenerator(),
        clock);
  }

  @Test
  void executeCreatesInviteAndPublishesEvent() {
    Meeting meeting = TestFixtures.mockMeeting("meeting-1", "room-1");
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
    when(inviteRepository.save(any(MeetingInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));

    MeetingInvite invite = useCase.execute(new CreateInviteCommand(
        "meeting-1",
        MeetingRole.PARTICIPANT,
        2,
        Instant.parse("2026-01-01T01:00:00Z"),
        "actor-1",
        "trace-1"));

    assertThat(invite.meetingId()).isEqualTo("meeting-1");
    verify(eventPublisher).publishEvent(any(MeetingInviteCreatedEvent.class));
  }

  @Test
  void executeRejectsInviteWhenGuestAccessIsDisabled() {
    Meeting meeting = meeting(false, Instant.parse("2026-01-01T02:00:00Z"));
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
    doThrow(new MeetingTokenException(
            HttpStatus.FORBIDDEN,
            ErrorCode.GUEST_ACCESS_DISABLED.code(),
            "Гостевой доступ для этой встречи отключён."))
        .when(meetingStateGuard)
        .assertGuestJoinAllowed(meeting);

    assertThatThrownBy(() -> useCase.execute(new CreateInviteCommand(
        "meeting-1",
        MeetingRole.PARTICIPANT,
        1,
        Instant.parse("2026-01-01T01:00:00Z"),
        "actor-1",
        "trace-1")))
        .isInstanceOfSatisfying(MeetingTokenException.class, ex -> {
          assertThat(ex.status()).isEqualTo(HttpStatus.FORBIDDEN);
          assertThat(ex.errorCode()).isEqualTo(ErrorCode.GUEST_ACCESS_DISABLED.code());
        });

    verifyNoInteractions(inviteRepository, eventPublisher);
  }

  @Test
  void executeRejectsModeratorGuestInvite() {
    when(meetingRepository.findById("meeting-1"))
        .thenReturn(Optional.of(meeting(true, Instant.parse("2026-01-01T02:00:00Z"))));

    assertThatThrownBy(() -> useCase.execute(new CreateInviteCommand(
        "meeting-1",
        MeetingRole.MODERATOR,
        1,
        Instant.parse("2026-01-01T01:00:00Z"),
        "actor-1",
        "trace-1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Guest invites support PARTICIPANT role only");
  }

  @Test
  void executeRequiresExpirationAndCapsItAtMeetingEnd() {
    Meeting meeting = meeting(true, Instant.parse("2026-01-01T00:30:00Z"));
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
    when(inviteRepository.save(any(MeetingInvite.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(() -> useCase.execute(new CreateInviteCommand(
        "meeting-1",
        MeetingRole.PARTICIPANT,
        1,
        null,
        "actor-1",
        "trace-null-expiry")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Guest invite expiration is required");

    MeetingInvite invite = useCase.execute(new CreateInviteCommand(
        "meeting-1",
        MeetingRole.PARTICIPANT,
        1,
        Instant.parse("2026-01-01T01:00:00Z"),
        "actor-1",
        "trace-capped-expiry"));

    assertThat(invite.expiresAt()).isEqualTo(meeting.endsAt());
  }

  private Meeting meeting(boolean allowGuests, Instant endsAt) {
    return Meeting.builder()
        .meetingId("meeting-1")
        .roomId("room-1")
        .title("Meeting")
        .description("Description")
        .meetingType("scheduled")
        .configSetId("config-1")
        .status(MeetingStatus.SCHEDULED)
        .startsAt(Instant.parse("2025-12-31T23:00:00Z"))
        .endsAt(endsAt)
        .allowGuests(allowGuests)
        .recordingEnabled(false)
        .createdAt(Instant.parse("2025-12-01T00:00:00Z"))
        .updatedAt(Instant.parse("2025-12-01T00:00:00Z"))
        .build();
  }
}
