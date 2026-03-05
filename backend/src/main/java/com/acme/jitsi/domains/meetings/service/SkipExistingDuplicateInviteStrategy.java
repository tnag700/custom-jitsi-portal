package com.acme.jitsi.domains.meetings.service;

import org.springframework.stereotype.Component;

@Component
public class SkipExistingDuplicateInviteStrategy implements DuplicateInviteHandlingStrategy {

  @Override
  public DuplicateHandlingPolicy policy() {
    return DuplicateHandlingPolicy.SKIP_EXISTING;
  }

  @Override
  public DuplicateInviteHandlingDecision onExistingInvite(MeetingInvite existingInvite) {
    return new DuplicateInviteHandlingDecision(true, false);
  }
}
