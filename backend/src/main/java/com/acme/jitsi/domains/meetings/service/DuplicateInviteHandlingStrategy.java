package com.acme.jitsi.domains.meetings.service;

public interface DuplicateInviteHandlingStrategy {

  DuplicateHandlingPolicy policy();

  DuplicateInviteHandlingDecision onExistingInvite(MeetingInvite existingInvite);
}
