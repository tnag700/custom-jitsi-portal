package com.acme.jitsi.domains.meetings.service;

import org.springframework.stereotype.Component;

@Component
public class ReplaceDuplicateInviteStrategy implements DuplicateInviteHandlingStrategy {

  @Override
  public DuplicateHandlingPolicy policy() {
    return DuplicateHandlingPolicy.REPLACE;
  }

  @Override
  public DuplicateInviteHandlingDecision onExistingInvite(MeetingInvite existingInvite) {
    return new DuplicateInviteHandlingDecision(false, true);
  }
}
