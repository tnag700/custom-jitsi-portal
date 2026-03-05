package com.acme.jitsi.domains.configsets.service;

public class ConfigSetNameConflictException extends RuntimeException {
  public ConfigSetNameConflictException(String name, String tenantId) {
    super("Config set with name '" + name + "' already exists in tenant '" + tenantId + "'");
  }
}