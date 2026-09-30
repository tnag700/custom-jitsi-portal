package com.acme.jitsi.domains.configsets.service;

@org.springframework.modulith.NamedInterface(value = "service", propagate = false)
public enum ConfigSetStatus {
  DRAFT,
  ACTIVE,
  INACTIVE
}
