package com.acme.jitsi.domains.auth.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("app.auth.refresh")
@Validated
class AuthRefreshProperties {

  private String atomicStore = "in-memory";

  @Min(1)
  @Max(1440)
  private int idleTtlMinutes = 60;

  private Set<String> revokedTokenIds = new HashSet<>();

  String atomicStore() {
    return atomicStore;
  }

  public void setAtomicStore(String atomicStore) {
    this.atomicStore = atomicStore;
  }

  int idleTtlMinutes() {
    return idleTtlMinutes;
  }

  public void setIdleTtlMinutes(int idleTtlMinutes) {
    this.idleTtlMinutes = idleTtlMinutes;
  }

  Set<String> revokedTokenIds() {
    return revokedTokenIds;
  }

  public void setRevokedTokenIds(Set<String> revokedTokenIds) {
    this.revokedTokenIds = revokedTokenIds;
  }
}
