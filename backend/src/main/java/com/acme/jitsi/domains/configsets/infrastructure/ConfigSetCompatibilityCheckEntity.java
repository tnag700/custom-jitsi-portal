package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "config_set_compatibility_checks")
class ConfigSetCompatibilityCheckEntity {

  @Id
  @Column(name = "check_id", nullable = false, updatable = false)
  private String checkId;

  @Column(name = "config_set_id", nullable = false)
  private String configSetId;

  @Column(name = "compatible", nullable = false)
  private boolean compatible;

  @Column(name = "mismatch_codes")
  private String mismatchCodes;

  @Column(name = "details")
  private String details;

  @Column(name = "checked_at", nullable = false)
  private Instant checkedAt;

  @Column(name = "trace_id")
  private String traceId;

  protected ConfigSetCompatibilityCheckEntity() {
  }

  ConfigSetCompatibilityCheckEntity(ConfigSetCompatibilityCheck check) {
    this.checkId = check.checkId();
    this.configSetId = check.configSetId();
    this.compatible = check.compatible();
    this.mismatchCodes = String.join(",", check.mismatchCodes());
    this.details = check.details();
    this.checkedAt = check.checkedAt();
    this.traceId = check.traceId();
  }

  ConfigSetCompatibilityCheck toDomain() {
    List<String> codes = mismatchCodes == null || mismatchCodes.isBlank()
        ? List.of()
        : List.of(mismatchCodes.split(","));
    return new ConfigSetCompatibilityCheck(
        checkId,
        configSetId,
        compatible,
        codes,
        details,
        checkedAt,
        traceId);
  }
}