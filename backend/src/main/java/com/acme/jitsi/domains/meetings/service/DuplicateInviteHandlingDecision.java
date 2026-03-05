package com.acme.jitsi.domains.meetings.service;

public record DuplicateInviteHandlingDecision(
    boolean skipExisting,
    boolean revokeExisting
) {}
