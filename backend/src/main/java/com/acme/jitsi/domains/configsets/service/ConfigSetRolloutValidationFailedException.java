package com.acme.jitsi.domains.configsets.service;

import java.util.List;

public class ConfigSetRolloutValidationFailedException extends RuntimeException {

  private final List<String> errors;

  public ConfigSetRolloutValidationFailedException(List<String> errors) {
    super("Config set rollout validation failed: " + String.join("; ", errors == null ? List.of() : errors));
    this.errors = errors == null ? List.of() : List.copyOf(errors);
  }

  public List<String> getErrors() {
    return errors;
  }
}