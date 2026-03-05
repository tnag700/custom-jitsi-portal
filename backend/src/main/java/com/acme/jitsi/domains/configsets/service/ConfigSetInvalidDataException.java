package com.acme.jitsi.domains.configsets.service;

public class ConfigSetInvalidDataException extends RuntimeException {
  public ConfigSetInvalidDataException(String message) {
    super(message);
  }

  public ConfigSetInvalidDataException(String message, Throwable cause) {
    super(message, cause);
  }
}