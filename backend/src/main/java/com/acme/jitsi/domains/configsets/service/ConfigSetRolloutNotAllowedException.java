package com.acme.jitsi.domains.configsets.service;

public class ConfigSetRolloutNotAllowedException extends RuntimeException {
  public ConfigSetRolloutNotAllowedException(String message) {
    super(message);
  }
}
