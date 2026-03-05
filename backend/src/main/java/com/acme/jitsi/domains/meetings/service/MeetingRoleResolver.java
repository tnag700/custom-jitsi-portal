package com.acme.jitsi.domains.meetings.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
class MeetingRoleResolver {

  private final MeetingTokenProperties properties;
  private final List<MeetingRoleResolutionPolicy> policies;

  MeetingRoleResolver(MeetingTokenProperties properties, List<MeetingRoleResolutionPolicy> policies) {
    this.properties = properties;
    this.policies = new ArrayList<>(policies);
    AnnotationAwareOrderComparator.sort(this.policies);
  }

  MeetingRole resolve(String meetingId, String subject) {
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext(meetingId, subject, properties);
    for (MeetingRoleResolutionPolicy policy : policies) {
      java.util.Optional<MeetingRole> resolved = policy.resolve(context);
      if (resolved.isPresent()) {
        return resolved.get();
      }
    }

    throw new IllegalStateException("No meeting role resolution policy produced a decision");
  }
}
