package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.event.MeetingBulkInviteCreatedEvent;
import com.acme.jitsi.domains.meetings.infrastructure.BulkInviteRecipientValidator;
import com.acme.jitsi.domains.meetings.service.BulkInviteError;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.BulkInviteRequest;
import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import com.acme.jitsi.domains.meetings.service.BulkInviteSkippedItem;
import com.acme.jitsi.domains.meetings.service.BulkInviteSummary;
import com.acme.jitsi.domains.meetings.service.BulkInviteValidationException;
import com.acme.jitsi.domains.meetings.service.BulkInviteCreatedItem;
import com.acme.jitsi.domains.meetings.service.DuplicateHandlingPolicy;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteHandlingDecision;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteHandlingStrategy;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteStrategyResolver;
import com.acme.jitsi.domains.meetings.service.InvalidRecipientFormatException;
import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingNotFoundException;
import com.acme.jitsi.domains.meetings.service.MeetingRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBulkInvitesUseCase implements UseCase<CreateBulkInvitesCommand, BulkInviteResult> {

  private final MeetingInviteRepository inviteRepository;
  private final MeetingRepository meetingRepository;
  private final MeetingStateGuard meetingStateGuard;
  private final ApplicationEventPublisher eventPublisher;
  private final SecureInviteTokenGenerator tokenGenerator;
  private final DuplicateInviteStrategyResolver duplicateStrategyResolver;
  private final BulkInviteRecipientValidator recipientValidator;
  private final Clock clock;

  public CreateBulkInvitesUseCase(
      MeetingInviteRepository inviteRepository,
      MeetingRepository meetingRepository,
      MeetingStateGuard meetingStateGuard,
      ApplicationEventPublisher eventPublisher,
      SecureInviteTokenGenerator tokenGenerator,
      DuplicateInviteStrategyResolver duplicateStrategyResolver,
      BulkInviteRecipientValidator recipientValidator,
      Clock clock) {
    this.inviteRepository = inviteRepository;
    this.meetingRepository = meetingRepository;
    this.meetingStateGuard = meetingStateGuard;
    this.eventPublisher = eventPublisher;
    this.tokenGenerator = tokenGenerator;
    this.duplicateStrategyResolver = duplicateStrategyResolver;
    this.recipientValidator = recipientValidator;
    this.clock = clock;
  }

  @Override
  @Transactional
  public BulkInviteResult execute(CreateBulkInvitesCommand command) {
    String meetingId = command.meetingId();
    BulkInviteRequest request = command.request();

    Meeting meeting = loadMeetingForInviteCreation(meetingId);
    List<BulkInviteRecipient> recipients = resolveRecipients(request);
    validateRecipientsInput(recipients);

    MeetingRole defaultRole = resolveDefaultRole(request);
    int defaultMaxUses = resolveDefaultMaxUses(request);
    Instant defaultExpiresAt = resolveDefaultExpiresAt(request.defaultTtlMinutes());
    DuplicateInviteHandlingStrategy strategy = resolveDuplicateStrategy(request);

    ProcessingState processing = processRecipients(
        command,
        meetingId,
        recipients,
        defaultRole,
        defaultMaxUses,
        defaultExpiresAt,
        strategy,
        Instant.now(clock));

    persistChanges(processing);

    BulkInviteResult result = new BulkInviteResult(
        List.copyOf(processing.created),
        List.copyOf(processing.skipped),
        List.copyOf(processing.errors),
        new BulkInviteSummary(recipients.size(), processing.created.size(), processing.skipped.size(), processing.errors.size()));

    eventPublisher.publishEvent(new MeetingBulkInviteCreatedEvent(
        meetingId,
        meeting.roomId(),
        command.actorId(),
        command.traceId(),
        "total=" + recipients.size() + ",success=" + processing.created.size() + ",failed=" + processing.errors.size() + ",skipped=" + processing.skipped.size()
    ));

    return result;
  }

  private Meeting loadMeetingForInviteCreation(String meetingId) {
    Meeting meeting = meetingRepository.findById(meetingId)
        .orElseThrow(() -> new MeetingNotFoundException(meetingId));
    meetingStateGuard.assertJoinAllowed(meetingId);
    return meeting;
  }

  private List<BulkInviteRecipient> resolveRecipients(BulkInviteRequest request) {
    return request.recipients() == null ? List.of() : request.recipients();
  }

  private void validateRecipientsInput(List<BulkInviteRecipient> recipients) {
    if (recipients.isEmpty()) {
      BulkInviteError error = new BulkInviteError(0, "", "INVALID_RECIPIENT_FORMAT", "Recipients list must not be empty");
      BulkInviteResult empty = new BulkInviteResult(List.of(), List.of(), List.of(error), new BulkInviteSummary(0, 0, 0, 1));
      throw new BulkInviteValidationException("Bulk invite validation failed", List.of(error), empty);
    }

    if (recipients.size() > 100) {
      BulkInviteError error = new BulkInviteError(0, "", "BULK_INVITE_VALIDATION_FAILED", "Recipients limit exceeded. Maximum is 100");
      BulkInviteResult empty = new BulkInviteResult(
          List.of(),
          List.of(),
          List.of(error),
          new BulkInviteSummary(recipients.size(), 0, 0, recipients.size()));
      throw new BulkInviteValidationException("Bulk invite validation failed", List.of(error), empty);
    }
  }

  private MeetingRole resolveDefaultRole(BulkInviteRequest request) {
    MeetingRole defaultRole = request.defaultRole() == null ? MeetingRole.PARTICIPANT : request.defaultRole();
    if (defaultRole == MeetingRole.HOST) {
      throw new IllegalArgumentException("defaultRole HOST is not allowed for invites");
    }
    return defaultRole;
  }

  private int resolveDefaultMaxUses(BulkInviteRequest request) {
    int defaultMaxUses = request.defaultMaxUses() == null ? 1 : request.defaultMaxUses();
    if (defaultMaxUses < 1) {
      throw new IllegalArgumentException("defaultMaxUses must be at least 1");
    }
    return defaultMaxUses;
  }

  private Instant resolveDefaultExpiresAt(Integer ttlMinutes) {
    if (ttlMinutes == null) {
      return null;
    }
    if (ttlMinutes < 1 || ttlMinutes > 10080) {
      throw new IllegalArgumentException("defaultTtlMinutes must be in range 1..10080");
    }
    return Instant.now(clock).plus(ttlMinutes, ChronoUnit.MINUTES);
  }

  private DuplicateInviteHandlingStrategy resolveDuplicateStrategy(BulkInviteRequest request) {
    DuplicateHandlingPolicy policy =
        request.duplicatePolicy() == null ? DuplicateHandlingPolicy.SKIP_EXISTING : request.duplicatePolicy();
    return duplicateStrategyResolver.resolve(policy);
  }

  private ProcessingState processRecipients(
      CreateBulkInvitesCommand command,
      String meetingId,
      List<BulkInviteRecipient> recipients,
      MeetingRole defaultRole,
      int defaultMaxUses,
      Instant defaultExpiresAt,
      DuplicateInviteHandlingStrategy strategy,
      Instant now) {
    ProcessingState processing = new ProcessingState();

    for (int index = 0; index < recipients.size(); index++) {
      BulkInviteRecipient normalized = normalizeRecipient(recipients.get(index), index);
      MeetingRole role = normalized.roleOverride() == null ? defaultRole : normalized.roleOverride();

      try {
        recipientValidator.validate(normalized, role);

        Optional<MeetingInvite> existing = findExistingActiveInvite(meetingId, normalized, now);
        if (existing.isPresent()) {
          DuplicateInviteHandlingDecision decision = strategy.onExistingInvite(existing.get());
          if (decision.skipExisting()) {
            processing.skipped.add(new BulkInviteSkippedItem(normalized.displayRecipient(), "EXISTING_ACTIVE_INVITE"));
            continue;
          }
          if (decision.revokeExisting()) {
            processing.invitesToRevoke.add(existing.get().withRevoked());
          }
        }

        MeetingInvite invite = createInvite(command, meetingId, normalized, role, defaultMaxUses, defaultExpiresAt);
        processing.invitesToSave.add(invite);
        processing.created.add(new BulkInviteCreatedItem(
            invite.id(),
            invite.token(),
            normalized.displayRecipient(),
            invite.role().value()));
      } catch (InvalidRecipientFormatException ex) {
        processing.errors.add(recipientValidator.toError(ex));
      }
    }

    return processing;
  }

  private BulkInviteRecipient normalizeRecipient(BulkInviteRecipient recipient, int index) {
    int rowIndex = recipient.rowIndex() > 0 ? recipient.rowIndex() : index + 1;
    return new BulkInviteRecipient(
        rowIndex,
        recipient.normalizedEmail(),
        recipient.normalizedUserId(),
        recipient.roleOverride());
  }

  private MeetingInvite createInvite(
      CreateBulkInvitesCommand command,
      String meetingId,
      BulkInviteRecipient normalized,
      MeetingRole role,
      int defaultMaxUses,
      Instant defaultExpiresAt) {
    return new MeetingInvite(
        UUID.randomUUID().toString(),
        meetingId,
        tokenGenerator.generateToken(),
        role,
        defaultMaxUses,
        0,
        defaultExpiresAt,
        null,
        Instant.now(clock),
        command.actorId(),
        normalized.normalizedEmail(),
        normalized.normalizedUserId(),
        0L);
  }

  private void persistChanges(ProcessingState processing) {
    if (!processing.invitesToRevoke.isEmpty()) {
      inviteRepository.saveAll(processing.invitesToRevoke);
    }
    if (!processing.invitesToSave.isEmpty()) {
      inviteRepository.saveAll(processing.invitesToSave);
    }
  }

  private static final class ProcessingState {
    private final List<BulkInviteCreatedItem> created = new ArrayList<>();
    private final List<BulkInviteSkippedItem> skipped = new ArrayList<>();
    private final List<BulkInviteError> errors = new ArrayList<>();
    private final List<MeetingInvite> invitesToSave = new ArrayList<>();
    private final List<MeetingInvite> invitesToRevoke = new ArrayList<>();
  }

  private Optional<MeetingInvite> findExistingActiveInvite(
      String meetingId,
      BulkInviteRecipient recipient,
      Instant now) {
    if (recipient.normalizedEmail() != null && !recipient.normalizedEmail().isBlank()) {
      return inviteRepository.findActiveByMeetingIdAndRecipientEmail(
          meetingId,
          recipient.normalizedEmail(),
          now);
    }

    return inviteRepository.findActiveByMeetingIdAndRecipientUserId(
        meetingId,
        recipient.normalizedUserId(),
        now);
  }
}
