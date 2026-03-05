package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingBulkInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.infrastructure.BulkInviteRecipientValidator;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.BulkInviteRequest;
import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import com.acme.jitsi.domains.meetings.service.BulkInviteValidationException;
import com.acme.jitsi.domains.meetings.service.DuplicateHandlingPolicy;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteStrategyResolver;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignmentRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.ReplaceDuplicateInviteStrategy;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import com.acme.jitsi.domains.meetings.service.SkipExistingDuplicateInviteStrategy;
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
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CreateBulkInvitesUseCaseTest {

  @Mock
  private MeetingInviteRepository inviteRepository;
  @Mock
  private MeetingRepository meetingRepository;
  @Mock
  private MeetingStateGuard meetingStateGuard;
  @Mock
  private ApplicationEventPublisher eventPublisher;
  @Mock
  private MeetingParticipantAssignmentRepository assignmentRepository;

  private CreateBulkInvitesUseCase useCase;

  @BeforeEach
  void setUp() {
    DuplicateInviteStrategyResolver resolver =
        new DuplicateInviteStrategyResolver(
            List.of(new SkipExistingDuplicateInviteStrategy(), new ReplaceDuplicateInviteStrategy()));

    useCase = new CreateBulkInvitesUseCase(
        inviteRepository,
        meetingRepository,
        meetingStateGuard,
        eventPublisher,
        new SecureInviteTokenGenerator(),
        resolver,
        new BulkInviteRecipientValidator(assignmentRepository),
        Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void executeCreatesBulkInvitesAndPublishesEvent() {
    Meeting meeting = mock(Meeting.class);
    when(meeting.roomId()).thenReturn("room-1");
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
    when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(any(), any(), any())).thenReturn(Optional.empty());
    when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    BulkInviteResult result = useCase.execute(new CreateBulkInvitesCommand(
        "meeting-1",
        new BulkInviteRequest(
            List.of(new BulkInviteRecipient(1, "user@example.com", null, MeetingRole.PARTICIPANT)),
            MeetingRole.PARTICIPANT,
            60,
            1,
            DuplicateHandlingPolicy.SKIP_EXISTING),
        "actor-1",
        "trace-1"));

    assertThat(result.summary().created()).isEqualTo(1);
    verify(eventPublisher).publishEvent(any(MeetingBulkInviteCreatedEvent.class));
  }

  @Test
  void executeThrowsWhenRecipientsIsEmpty() {
    Meeting meeting = mock(Meeting.class);
    when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));

    assertThatThrownBy(() -> useCase.execute(new CreateBulkInvitesCommand(
        "meeting-1",
        new BulkInviteRequest(
            List.of(),
            MeetingRole.PARTICIPANT,
            60,
            1,
            DuplicateHandlingPolicy.SKIP_EXISTING),
        "actor-1",
        "trace-1")))
        .isInstanceOf(BulkInviteValidationException.class);
  }
}
