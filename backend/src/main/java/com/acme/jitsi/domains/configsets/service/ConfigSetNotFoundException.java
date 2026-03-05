package com.acme.jitsi.domains.configsets.service;

public class ConfigSetNotFoundException extends RuntimeException {
  public ConfigSetNotFoundException(String configSetId) {
    super("Config set '" + configSetId + "' not found");
  }
}