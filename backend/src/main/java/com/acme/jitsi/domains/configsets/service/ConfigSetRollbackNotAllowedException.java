package com.acme.jitsi.domains.configsets.service;

public class ConfigSetRollbackNotAllowedException extends RuntimeException {
  public ConfigSetRollbackNotAllowedException(String message) {
    super(message);
  }
}