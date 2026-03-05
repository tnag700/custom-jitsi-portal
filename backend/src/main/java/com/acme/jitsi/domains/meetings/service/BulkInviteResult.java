package com.acme.jitsi.domains.meetings.service;

import java.util.List;

public record BulkInviteResult(
    List<BulkInviteCreatedItem> created,
    List<BulkInviteSkippedItem> skipped,
    List<BulkInviteError> errors,
    BulkInviteSummary summary
) {}
