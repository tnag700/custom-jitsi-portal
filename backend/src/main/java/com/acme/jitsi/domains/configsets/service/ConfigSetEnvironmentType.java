package com.acme.jitsi.domains.configsets.service;

@org.springframework.modulith.NamedInterface(value = "service", propagate = false)
public enum ConfigSetEnvironmentType {
  DEV,
  TEST,
  PROD
}
