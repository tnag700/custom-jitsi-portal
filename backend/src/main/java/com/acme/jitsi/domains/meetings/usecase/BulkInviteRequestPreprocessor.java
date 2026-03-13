package com.acme.jitsi.domains.meetings.usecase;

import com.acme.jitsi.domains.meetings.service.BulkInviteError;
import com.acme.jitsi.domains.meetings.service.BulkInviteRecipient;
import com.acme.jitsi.domains.meetings.service.BulkInviteRequest;
import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import com.acme.jitsi.domains.meetings.service.BulkInviteSummary;
import com.acme.jitsi.domains.meetings.service.BulkInviteValidationException;
import com.acme.jitsi.domains.meetings.service.DuplicateHandlingPolicy;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteHandlingStrategy;
import com.acme.jitsi.domains.meetings.service.DuplicateInviteStrategyResolver;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import com.acme.jitsi.shared.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class BulkInviteRequestPreprocessor {

  private final DuplicateInviteStrategyResolver duplicateStrategyResolver;
  private final Clock clock;

  BulkInviteRequestPreprocessor(
      DuplicateInviteStrategyResolver duplicateStrategyResolver, Clock clock) {
    this.duplicateStrategyResolver = duplicateStrategyResolver;
    this.clock = clock;
  }

  PreparedBulkInviteRequest prepare(BulkInviteRequest request) {
    List<BulkInviteRecipient> recipients = request.recipients() == null ? List.of() : request.recipients();
    validateRecipientsInput(recipients);

    MeetingRole defaultRole = resolveDefaultRole(request);
    int defaultMaxUses = resolveDefaultMaxUses(request);
    Instant defaultExpiresAt = resolveDefaultExpiresAt(request.defaultTtlMinutes());
    DuplicateInviteHandlingStrategy strategy = resolveDuplicateStrategy(request);

    return new PreparedBulkInviteRequest(
        recipients, defaultRole, defaultMaxUses, defaultExpiresAt, strategy);
  }

  private void validateRecipientsInput(List<BulkInviteRecipient> recipients) {
    if (recipients.isEmpty()) {
      BulkInviteError error =
          new BulkInviteError(
              0,
              "",
              ErrorCode.INVALID_RECIPIENT_FORMAT.code(),
              "Recipients list must not be empty");
      BulkInviteResult empty =
          new BulkInviteResult(List.of(), List.of(), List.of(error), new BulkInviteSummary(0, 0, 0, 1));
      throw new BulkInviteValidationException("Bulk invite validation failed", List.of(error), empty);
    }

    if (recipients.size() > 100) {
      BulkInviteError error =
          new BulkInviteError(
              0,
              "",
              ErrorCode.BULK_INVITE_VALIDATION_FAILED.code(),
              "Recipients limit exceeded. Maximum is 100");
      BulkInviteResult empty =
          new BulkInviteResult(
              List.of(),
              List.of(),
              List.of(error),
              new BulkInviteSummary(recipients.size(), 0, 0, 1));
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
}