package com.acme.jitsi.domains.invites.service;

import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
class InviteValidationChain {

  private final List<InviteValidator> validators;

  InviteValidationChain(List<InviteValidator> validators) {
    List<InviteValidator> sortedValidators = new ArrayList<>(validators);
    AnnotationAwareOrderComparator.sort(sortedValidators);
    this.validators = List.copyOf(sortedValidators);
  }

  void validate(InviteValidationContext context) {
    for (InviteValidator validator : validators) {
      validator.validate(context);
    }
  }
}