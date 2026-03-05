package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetRollout;
import com.acme.jitsi.domains.configsets.service.RolloutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "config_set_rollouts")
class ConfigSetRolloutEntity {

  @Id
  @Column(name = "rollout_id", nullable = false, updatable = false)
  private String rolloutId;

  @Column(name = "config_set_id", nullable = false)
  private String configSetId;

  @Column(name = "previous_config_set_id")
  private String previousConfigSetId;

  @Column(name = "tenant_id", nullable = false)
  private String tenantId;

  @Enumerated(EnumType.STRING)
  @Column(name = "environment_type", nullable = false)
  private ConfigSetEnvironmentType environmentType;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private RolloutStatus status;

  @Column(name = "validation_errors")
  private String validationErrors;

  @Column(name = "started_at", nullable = false)
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  @Column(name = "trace_id")
  private String traceId;

  protected ConfigSetRolloutEntity() {
  }

  ConfigSetRolloutEntity(ConfigSetRollout rollout) {
    this.rolloutId = rollout.rolloutId();
    this.configSetId = rollout.configSetId();
    this.previousConfigSetId = rollout.previousConfigSetId();
    this.tenantId = rollout.tenantId();
    this.environmentType = rollout.environmentType();
    this.status = rollout.status();
    this.validationErrors = rollout.validationErrors();
    this.startedAt = rollout.startedAt();
    this.completedAt = rollout.completedAt();
    this.actorId = rollout.actorId();
    this.traceId = rollout.traceId();
  }

  void updateFrom(ConfigSetRollout rollout) {
    this.configSetId = rollout.configSetId();
    this.previousConfigSetId = rollout.previousConfigSetId();
    this.tenantId = rollout.tenantId();
    this.environmentType = rollout.environmentType();
    this.status = rollout.status();
    this.validationErrors = rollout.validationErrors();
    this.startedAt = rollout.startedAt();
    this.completedAt = rollout.completedAt();
    this.actorId = rollout.actorId();
    this.traceId = rollout.traceId();
  }

  ConfigSetRollout toDomain() {
    return new ConfigSetRollout(
        rolloutId,
        configSetId,
        previousConfigSetId,
        tenantId,
        environmentType,
        status,
        validationErrors,
        startedAt,
        completedAt,
        actorId,
        traceId);
  }
}