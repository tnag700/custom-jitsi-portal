package com.acme.jitsi.domains.rooms.service;

public class ConfigSetInvalidException extends RuntimeException {
  public ConfigSetInvalidException(String configSetId) {
    super("Config set '" + configSetId + "' is invalid or not validated");
  }
}
