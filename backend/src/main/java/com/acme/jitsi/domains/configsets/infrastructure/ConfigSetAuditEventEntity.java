package com.acme.jitsi.domains.configsets.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "config_set_audit_events")
class ConfigSetAuditEventEntity {

  @Id
  @Column(name = "event_id", nullable = false, updatable = false)
  private String eventId;

  @Column(name = "config_set_id", nullable = false)
  private String configSetId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "actor_id", nullable = false)
  private String actorId;

  @Column(name = "trace_id")
  private String traceId;

  @Column(name = "changed_fields")
  private String changedFields;

  @Column(name = "old_values")
  private String oldValues;

  @Column(name = "new_values")
  private String newValues;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected ConfigSetAuditEventEntity() {
  }

  ConfigSetAuditEventEntity(
      String eventId,
      String configSetId,
      String eventType,
      String actorId,
      String traceId,
      String changedFields,
      String oldValues,
      String newValues,
      Instant occurredAt) {
    this.eventId = eventId;
    this.configSetId = configSetId;
    this.eventType = eventType;
    this.actorId = actorId;
    this.traceId = traceId;
    this.changedFields = changedFields;
    this.oldValues = oldValues;
    this.newValues = newValues;
    this.occurredAt = occurredAt;
  }
}