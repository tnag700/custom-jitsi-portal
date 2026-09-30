package com.acme.jitsi.domains.configsets.service;

@org.springframework.modulith.NamedInterface(value = "service", propagate = false)
public class ConfigSetNotFoundException extends RuntimeException {
  public ConfigSetNotFoundException(String configSetId) {
    super("Config set '" + configSetId + "' not found");
  }
}
