package com.acme.jitsi.domains.configsets.service;

public class ConfigSetActivationNotAllowedException extends RuntimeException {
  public ConfigSetActivationNotAllowedException(String message) {
    super(message);
  }
}