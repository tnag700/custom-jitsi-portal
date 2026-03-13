package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.BulkInviteCreatedItem;
import com.acme.jitsi.domains.meetings.service.BulkInviteError;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipientValidator;
import com.acme.jitsi.domains.meetings.service.BulkInviteSkippedItem;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteHandlingDecision;
import com.acme.jitsi.domains.meetings.service.InvalidRecipientFormatException;
import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingInviteRepository;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.domains.meetings.service.SecureInviteTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
final class BulkInviteRecipientProcessor {

  private final MeetingInviteRepository inviteRepository;
  private final BulkInviteRecipientValidator recipientValidator;
  private final SecureInviteTokenGenerator tokenGenerator;
  private final Clock clock;

  BulkInviteRecipientProcessor(
      MeetingInviteRepository inviteRepository,
      BulkInviteRecipientValidator recipientValidator,
      SecureInviteTokenGenerator tokenGenerator,
      Clock clock) {
    this.inviteRepository = inviteRepository;
    this.recipientValidator = recipientValidator;
    this.tokenGenerator = tokenGenerator;
    this.clock = clock;
  }

  ProcessingState process(CreateBulkInvitesCommand command, PreparedBulkInviteRequest request) {
    ProcessingState processing = new ProcessingState();
    Instant now = Instant.now(clock);

    for (int index = 0; index < request.recipients().size(); index++) {
      BulkInviteRecipient normalized = normalizeRecipient(request.recipients().get(index), index);
      MeetingRole role = normalized.roleOverride() == null ? request.defaultRole() : normalized.roleOverride();

      try {
        recipientValidator.validate(normalized, role);
        applyDuplicateHandling(command, request, processing, normalized, role, now);
      } catch (InvalidRecipientFormatException ex) {
        processing.errors.add(recipientValidator.toError(ex));
      }
    }

    return processing;
  }

  private void applyDuplicateHandling(
      CreateBulkInvitesCommand command,
      PreparedBulkInviteRequest request,
      ProcessingState processing,
      BulkInviteRecipient normalized,
      MeetingRole role,
      Instant now) {
    String recipientKey = recipientKey(normalized);
    Optional<MeetingInvite> existing =
        findExistingActiveInvite(command.meetingId(), normalized, now, processing.activeInvitesByRecipient);
    if (existing.isPresent()) {
      DuplicateInviteHandlingDecision decision = request.strategy().onExistingInvite(existing.get());
      if (decision.skipExisting()) {
        processing.skipped.add(
            new BulkInviteSkippedItem(normalized.displayRecipient(), "EXISTING_ACTIVE_INVITE"));
        return;
      }
      if (decision.revokeExisting()) {
        revokeExistingInvite(existing.get(), recipientKey, processing);
      }
    }

    MeetingInvite invite = createInvite(command, normalized, role, request.defaultMaxUses(), request.defaultExpiresAt());
    processing.invitesToSave.add(invite);
    BulkInviteCreatedItem createdItem =
        new BulkInviteCreatedItem(
            invite.id(), invite.token(), normalized.displayRecipient(), invite.role().value());
    processing.created.add(createdItem);
    processing.activeInvitesByRecipient.put(recipientKey, invite);
    processing.createdItemsByRecipient.put(recipientKey, createdItem);
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
      BulkInviteRecipient normalized,
      MeetingRole role,
      int defaultMaxUses,
      Instant defaultExpiresAt) {
    return new MeetingInvite(
        UUID.randomUUID().toString(),
        command.meetingId(),
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

  private Optional<MeetingInvite> findExistingActiveInvite(
      String meetingId,
      BulkInviteRecipient recipient,
      Instant now,
      Map<String, MeetingInvite> activeInvitesByRecipient) {
    MeetingInvite stagedInvite = activeInvitesByRecipient.get(recipientKey(recipient));
    if (stagedInvite != null) {
      return Optional.of(stagedInvite);
    }

    if (recipient.normalizedEmail() != null && !recipient.normalizedEmail().isBlank()) {
      return inviteRepository.findActiveByMeetingIdAndRecipientEmail(
          meetingId, recipient.normalizedEmail(), now);
    }

    return inviteRepository.findActiveByMeetingIdAndRecipientUserId(
        meetingId, recipient.normalizedUserId(), now);
  }

  private void revokeExistingInvite(
      MeetingInvite existing,
      String recipientKey,
      ProcessingState processing) {
    MeetingInvite stagedInvite = processing.activeInvitesByRecipient.get(recipientKey);
    if (stagedInvite != null && stagedInvite.id().equals(existing.id())) {
      processing.invitesToSave.removeIf(invite -> invite.id().equals(existing.id()));
      BulkInviteCreatedItem stagedCreated = processing.createdItemsByRecipient.remove(recipientKey);
      if (stagedCreated != null) {
        processing.created.remove(stagedCreated);
      }
      processing.activeInvitesByRecipient.remove(recipientKey);
      return;
    }

    boolean alreadyMarkedForRevoke =
        processing.invitesToRevoke.stream().anyMatch(invite -> invite.id().equals(existing.id()));
    if (!alreadyMarkedForRevoke) {
      processing.invitesToRevoke.add(existing.withRevoked());
    }
  }

  private String recipientKey(BulkInviteRecipient recipient) {
    if (recipient.normalizedEmail() != null && !recipient.normalizedEmail().isBlank()) {
      return "email:" + recipient.normalizedEmail();
    }
    return "user:" + recipient.normalizedUserId();
  }

  static final class ProcessingState {
    private final List<BulkInviteCreatedItem> created = new ArrayList<>();
    private final List<BulkInviteSkippedItem> skipped = new ArrayList<>();
    private final List<BulkInviteError> errors = new ArrayList<>();
    private final List<MeetingInvite> invitesToSave = new ArrayList<>();
    private final List<MeetingInvite> invitesToRevoke = new ArrayList<>();
    final Map<String, MeetingInvite> activeInvitesByRecipient = new HashMap<>();
    final Map<String, BulkInviteCreatedItem> createdItemsByRecipient = new HashMap<>();

    List<BulkInviteCreatedItem> created() {
      return created;
    }

    List<BulkInviteSkippedItem> skipped() {
      return skipped;
    }

    List<BulkInviteError> errors() {
      return errors;
    }

    List<MeetingInvite> invitesToSave() {
      return invitesToSave;
    }

    List<MeetingInvite> invitesToRevoke() {
      return invitesToRevoke;
    }
  }
}