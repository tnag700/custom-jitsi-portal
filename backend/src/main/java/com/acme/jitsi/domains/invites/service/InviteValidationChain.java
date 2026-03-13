package com.acme.jitsi.domains.invites.service;

import java.util.ArrayList;
import java.util.List;
import com.acme.jitsi.shared.pipeline.OrderedPipelineConfigurationException;
import com.acme.jitsi.shared.pipeline.OrderedTerminalPipelineSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

@Component
class InviteValidationChain {

  private final List<InviteValidator> validators;

  InviteValidationChain(List<InviteValidator> validators) {
    List<InviteValidator> orderedValidators = OrderedTerminalPipelineSupport.sortAndValidate(
        "InviteValidationChain",
        new ArrayList<>(validators),
        InviteValidator::isTerminalValidator,
        validator -> ClassUtils.getUserClass(validator).getSimpleName(),
        OrderedTerminalPipelineSupport.expectedSequence(
            InviteTokenBlankValidator.class,
            InviteTokenExistsValidator.class,
            InviteRevokedValidator.class,
            InviteExpirationValidator.class,
            InviteMeetingKnownValidator.class,
            InviteMeetingStateValidator.class,
            InviteUsageLimitValidator.class));
    validateInviteAssembly(orderedValidators);
    this.validators = orderedValidators;
  }

  void validate(InviteValidationContext context) {
    for (InviteValidator validator : validators) {
      if (validator.requiresResolvedInvite() && context.invite() == null) {
        throw new OrderedPipelineConfigurationException(
            "InviteValidationChain validator " + validator.getClass().getSimpleName()
                + " requires a loaded invite before execution.");
      }

      validator.validate(context);

      if (validator.loadsResolvedInvite() && context.invite() == null) {
        throw new OrderedPipelineConfigurationException(
            "InviteValidationChain invite-loading validator " + validator.getClass().getSimpleName()
                + " completed without loading an invite.");
      }

      if (validator.isTerminalValidator()) {
        return;
      }
    }

    throw new OrderedPipelineConfigurationException(
      "InviteValidationChain reached the end of the pipeline without a terminal validator.");
  }

  private static void validateInviteAssembly(List<InviteValidator> validators) {
    InviteValidator inviteLoadingValidator = null;
    for (InviteValidator validator : validators) {
      if (validator.loadsResolvedInvite()) {
        if (inviteLoadingValidator != null) {
          throw new OrderedPipelineConfigurationException(
              "InviteValidationChain requires exactly one invite-loading validator but found multiple: "
                  + inviteLoadingValidator.getClass().getSimpleName() + ", " + validator.getClass().getSimpleName());
        }
        inviteLoadingValidator = validator;
      }

      if (validator.requiresResolvedInvite() && inviteLoadingValidator == null) {
        throw new OrderedPipelineConfigurationException(
            "InviteValidationChain requires an invite-loading validator before "
                + validator.getClass().getSimpleName() + ".");
      }
    }

    if (inviteLoadingValidator == null) {
      throw new OrderedPipelineConfigurationException(
          "InviteValidationChain requires an invite-loading validator before terminal validation.");
    }
  }
}