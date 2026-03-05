package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.BulkInviteResult;
import java.util.List;

public record BulkInviteResponse(
    List<BulkInviteCreatedItemDto> created,
    List<BulkInviteSkippedItemDto> skipped,
    List<BulkInviteErrorItem> errors,
    BulkInviteSummaryDto summary
) {
  public static BulkInviteResponse fromDomain(BulkInviteResult result) {
    return new BulkInviteResponse(
        result.created().stream()
            .map(item -> new BulkInviteCreatedItemDto(
                item.inviteId(),
                item.token(),
                item.recipient(),
                item.role()))
            .toList(),
        result.skipped().stream()
            .map(item -> new BulkInviteSkippedItemDto(item.recipient(), item.reason()))
            .toList(),
        result.errors().stream()
            .map(error -> new BulkInviteErrorItem(
                error.rowIndex(),
                error.recipient(),
                error.errorCode(),
                error.message()))
            .toList(),
        new BulkInviteSummaryDto(
            result.summary().total(),
            result.summary().created(),
            result.summary().skipped(),
            result.summary().failed()));
  }

  public record BulkInviteCreatedItemDto(
      String inviteId,
      String token,
      String recipient,
      String role
  ) {}

  public record BulkInviteSkippedItemDto(
      String recipient,
      String reason
  ) {}

  public record BulkInviteSummaryDto(
      int total,
      int created,
      int skipped,
      int failed
  ) {}
}
