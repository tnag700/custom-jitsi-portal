package com.acme.jitsi.domains.meetings.service;

import java.util.ArrayList;
import java.util.List;
import com.acme.jitsi.shared.pipeline.OrderedPipelineConfigurationException;
import com.acme.jitsi.shared.pipeline.OrderedTerminalPipelineSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Component
class MeetingRoleResolver {

  private final MeetingTokenProperties properties;
  private final List<MeetingRoleResolutionPolicy> policies;

  MeetingRoleResolver(MeetingTokenProperties properties, List<MeetingRoleResolutionPolicy> policies) {
    this.properties = properties;
    this.policies = OrderedTerminalPipelineSupport.sortAndValidate(
        "MeetingRoleResolver",
        new ArrayList<>(policies),
        MeetingRoleResolutionPolicy::isTerminalPolicy,
        policy -> ClassUtils.getUserClass(policy).getSimpleName(),
        OrderedTerminalPipelineSupport.expectedSequence(
            BlockedSubjectMeetingRoleResolutionPolicy.class,
            UnknownMeetingMeetingRoleResolutionPolicy.class,
            DbParticipantAssignmentMeetingRoleResolutionPolicy.class,
            ExplicitAssignmentMeetingRoleResolutionPolicy.class,
            UnknownRolePolicyMeetingRoleResolutionPolicy.class));
  }

  MeetingRole resolve(String meetingId, String subject) {
    MeetingRoleResolutionContext context = new MeetingRoleResolutionContext(meetingId, subject, properties);
    for (MeetingRoleResolutionPolicy policy : policies) {
      java.util.Optional<MeetingRole> resolved = policy.resolve(context);
      if (resolved.isPresent()) {
        return resolved.get();
      }

      if (policy.isTerminalPolicy()) {
        throw new OrderedPipelineConfigurationException(
            "MeetingRoleResolver terminal policy " + policy.getClass().getSimpleName()
                + " completed without producing a decision.");
      }
    }

    throw new OrderedPipelineConfigurationException(
        "MeetingRoleResolver reached the end of the pipeline without a terminal policy.");
  }
}
