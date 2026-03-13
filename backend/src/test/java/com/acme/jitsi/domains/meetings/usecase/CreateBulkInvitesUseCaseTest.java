package com.acme.jitsi.domains.meetings.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.event.MeetingBulkInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipientValidator;
import com.acme.jitsi.domains.meetings.service.BulkInviteRequest;
import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import com.acme.jitsi.domains.meetings.service.BulkInviteValidationException;
import com.acme.jitsi.domains.meetings.service.DuplicateHandlingPolicy;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteStrategyResolver;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignmentRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.ReplaceDuplicateInviteStrategy;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import com.acme.jitsi.domains.meetings.service.SkipExistingDuplicateInviteStrategy;
import com.acme.jitsi.shared.TestFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
    Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    DuplicateInviteStrategyResolver resolver =
        new DuplicateInviteStrategyResolver(
            List.of(new SkipExistingDuplicateInviteStrategy(), new ReplaceDuplicateInviteStrategy()));
    BulkInviteRequestPreprocessor requestPreprocessor =
      new BulkInviteRequestPreprocessor(resolver, clock);
    BulkInviteRecipientProcessor recipientProcessor =
      new BulkInviteRecipientProcessor(
        inviteRepository,
        new BulkInviteRecipientValidator(assignmentRepository),
        new SecureInviteTokenGenerator(),
        clock);

    useCase = new CreateBulkInvitesUseCase(
        inviteRepository,
        meetingRepository,
        meetingStateGuard,
        eventPublisher,
      requestPreprocessor,
      recipientProcessor);
  }

  @Test
  void executeCreatesBulkInvitesAndPublishesEvent() {
    Meeting meeting = TestFixtures.mockMeeting("meeting-1", "room-1");
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
    Meeting meeting = TestFixtures.mockMeeting("meeting-1", "room-1");
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

      @Test
      void executeSkipsExistingInviteWhenPolicyIsSkipExisting() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));

      MeetingInvite existingInvite =
        new MeetingInvite(
          "invite-1",
          "meeting-1",
          "token-1",
          MeetingRole.PARTICIPANT,
          1,
          0,
          Instant.parse("2026-01-01T01:00:00Z"),
          null,
          Instant.parse("2026-01-01T00:00:00Z"),
          "actor-0",
          "user@example.com",
          null,
          0L);

      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("user@example.com"), any()))
        .thenReturn(Optional.of(existingInvite));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(new BulkInviteRecipient(1, "user@example.com", null, MeetingRole.PARTICIPANT)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.SKIP_EXISTING),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().created()).isEqualTo(0);
      assertThat(result.summary().skipped()).isEqualTo(1);
      verify(inviteRepository, never()).saveAll(any());
      }

      @Test
      void executeReplacesExistingInviteWhenPolicyIsReplace() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));

      MeetingInvite existingInvite =
        new MeetingInvite(
          "invite-1",
          "meeting-1",
          "token-1",
          MeetingRole.PARTICIPANT,
          1,
          0,
          Instant.parse("2026-01-01T01:00:00Z"),
          null,
          Instant.parse("2026-01-01T00:00:00Z"),
          "actor-0",
          "user@example.com",
          null,
          0L);

      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("user@example.com"), any()))
        .thenReturn(Optional.of(existingInvite));
      when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(new BulkInviteRecipient(1, "user@example.com", null, MeetingRole.PARTICIPANT)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.REPLACE),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().created()).isEqualTo(1);
      assertThat(result.summary().skipped()).isEqualTo(0);
      verify(inviteRepository, times(2)).saveAll(any());
      }

      @Test
      void executeSkipsDuplicateRecipientInsideSameRequestWhenPolicyIsSkipExisting() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("user@example.com"), any()))
        .thenReturn(Optional.empty());
      when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(
                new BulkInviteRecipient(1, "user@example.com", null, MeetingRole.PARTICIPANT),
                new BulkInviteRecipient(2, "user@example.com", null, MeetingRole.PARTICIPANT)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.SKIP_EXISTING),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().total()).isEqualTo(2);
      assertThat(result.summary().created()).isEqualTo(1);
      assertThat(result.summary().skipped()).isEqualTo(1);
      assertThat(result.skipped()).hasSize(1);
      verify(inviteRepository, times(1)).saveAll(any());
      }

      @Test
      void executeReplacesDuplicateRecipientInsideSameRequestWhenPolicyIsReplace() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("user@example.com"), any()))
        .thenReturn(Optional.empty());
      when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(
                new BulkInviteRecipient(1, "user@example.com", null, MeetingRole.PARTICIPANT),
                new BulkInviteRecipient(2, "user@example.com", null, MeetingRole.MODERATOR)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.REPLACE),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().total()).isEqualTo(2);
      assertThat(result.summary().created()).isEqualTo(1);
      assertThat(result.summary().skipped()).isEqualTo(0);
      assertThat(result.created()).singleElement().satisfies(item -> assertThat(item.role()).isEqualTo("moderator"));
      verify(inviteRepository, times(1)).saveAll(any());
      }

      @Test
      void executeAggregatesCreatedSkippedAndErrorsInSingleResult() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));

      MeetingInvite existingInvite =
        new MeetingInvite(
          "invite-existing",
          "meeting-1",
          "token-existing",
          MeetingRole.PARTICIPANT,
          1,
          0,
          Instant.parse("2026-01-01T01:00:00Z"),
          null,
          Instant.parse("2026-01-01T00:00:00Z"),
          "actor-0",
          "skip@example.com",
          null,
          0L);

      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("skip@example.com"), any()))
        .thenReturn(Optional.of(existingInvite));
      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("create@example.com"), any()))
        .thenReturn(Optional.empty());
      when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(
                new BulkInviteRecipient(1, "create@example.com", null, MeetingRole.PARTICIPANT),
                new BulkInviteRecipient(2, "skip@example.com", null, MeetingRole.PARTICIPANT),
                new BulkInviteRecipient(3, "broken-email", null, MeetingRole.PARTICIPANT)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.SKIP_EXISTING),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().total()).isEqualTo(3);
      assertThat(result.summary().created()).isEqualTo(1);
      assertThat(result.summary().skipped()).isEqualTo(1);
      assertThat(result.summary().failed()).isEqualTo(1);
      assertThat(result.created()).singleElement().satisfies(item -> {
        assertThat(item.recipient()).isEqualTo("create@example.com");
        assertThat(item.role()).isEqualTo("participant");
      });
      assertThat(result.skipped()).singleElement().satisfies(item -> {
        assertThat(item.recipient()).isEqualTo("skip@example.com");
        assertThat(item.reason()).isEqualTo("EXISTING_ACTIVE_INVITE");
      });
      assertThat(result.errors()).singleElement().satisfies(item -> {
        assertThat(item.rowIndex()).isEqualTo(3);
        assertThat(item.recipient()).isEqualTo("broken-email");
        assertThat(item.errorCode()).isEqualTo("INVALID_RECIPIENT_FORMAT");
        assertThat(item.message()).isEqualTo("Invalid email format");
      });
      }

      @Test
      void executePersistsRevocationsBeforeNewInvitesWhenReplacingPersistedDuplicate() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));

      MeetingInvite existingInvite =
        new MeetingInvite(
          "invite-1",
          "meeting-1",
          "token-1",
          MeetingRole.PARTICIPANT,
          1,
          0,
          Instant.parse("2026-01-01T01:00:00Z"),
          null,
          Instant.parse("2026-01-01T00:00:00Z"),
          "actor-0",
          "user@example.com",
          null,
          0L);

      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("user@example.com"), any()))
        .thenReturn(Optional.of(existingInvite));
      when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(new BulkInviteRecipient(1, "user@example.com", null, MeetingRole.PARTICIPANT)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.REPLACE),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().created()).isEqualTo(1);
      assertThat(result.summary().skipped()).isEqualTo(0);
      assertThat(result.summary().failed()).isEqualTo(0);

      InOrder inOrder = inOrder(inviteRepository);
      inOrder.verify(inviteRepository)
        .saveAll(argThat(invites ->
          invites.size() == 1
            && invites.getFirst().id().equals("invite-1")
            && invites.getFirst().isRevoked()));
      inOrder.verify(inviteRepository)
        .saveAll(argThat(invites ->
          invites.size() == 1
            && invites.getFirst().recipientEmail().equals("user@example.com")
            && !invites.getFirst().isRevoked()));
      }

      @Test
      void executeAggregatesRecipientFailuresWithoutAbortingValidRecipients() {
      Meeting meeting = mock(Meeting.class);
      when(meeting.roomId()).thenReturn("room-1");
      when(meetingRepository.findById("meeting-1")).thenReturn(Optional.of(meeting));
      when(inviteRepository.findActiveByMeetingIdAndRecipientEmail(eq("meeting-1"), eq("valid@example.com"), any()))
        .thenReturn(Optional.empty());
      when(assignmentRepository.findBySubjectId("11111111-1111-4111-8111-111111111111"))
        .thenReturn(List.of());
      when(inviteRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

      BulkInviteResult result =
        useCase.execute(
          new CreateBulkInvitesCommand(
            "meeting-1",
            new BulkInviteRequest(
              List.of(
                new BulkInviteRecipient(1, "valid@example.com", null, MeetingRole.PARTICIPANT),
                new BulkInviteRecipient(2, null, "11111111-1111-4111-8111-111111111111", MeetingRole.PARTICIPANT),
                new BulkInviteRecipient(3, null, "not-a-uuid", MeetingRole.PARTICIPANT)),
              MeetingRole.PARTICIPANT,
              60,
              1,
              DuplicateHandlingPolicy.SKIP_EXISTING),
            "actor-1",
            "trace-1"));

      assertThat(result.summary().created()).isEqualTo(1);
      assertThat(result.summary().failed()).isEqualTo(2);
      assertThat(result.errors())
        .extracting(error -> error.recipient() + ":" + error.message())
        .containsExactlyInAnyOrder(
          "11111111-1111-4111-8111-111111111111:Unknown userId",
          "not-a-uuid:userId must be a valid UUID");
      }
}
