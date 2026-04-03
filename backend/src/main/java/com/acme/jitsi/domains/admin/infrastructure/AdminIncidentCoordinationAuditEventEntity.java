package com.acme.jitsi.domains.admin.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_incident_coordination_audit_events")
class AdminIncidentCoordinationAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "incident_id", nullable = false, length = 64)
  private String incidentId;

  @Column(name = "tenant_id", nullable = false, length = 255)
  private String tenantId;

  @Column(name = "environment", nullable = false, length = 32)
  private String environment;

  @Column(name = "actor_id", nullable = false, length = 255)
  private String actorId;

  @Column(name = "action_type", nullable = false, length = 64)
  private String actionType;

  @Column(name = "trace_id", length = 128)
  private String traceId;

  @Column(name = "from_state", nullable = false, columnDefinition = "TEXT")
  private String fromState;

  @Column(name = "to_state", nullable = false, columnDefinition = "TEXT")
  private String toState;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AdminIncidentCoordinationAuditEventEntity() {
  }

  AdminIncidentCoordinationAuditEventEntity(
      String incidentId,
      String tenantId,
      String environment,
      String actorId,
      String actionType,
      String traceId,
      String fromState,
      String toState,
      Instant createdAt) {
    this.incidentId = incidentId;
    this.tenantId = tenantId;
    this.environment = environment;
    this.actorId = actorId;
    this.actionType = actionType;
    this.traceId = traceId;
    this.fromState = fromState;
    this.toState = toState;
    this.createdAt = createdAt;
  }

  Long id() {
    return id;
  }

  String actorId() {
    return actorId;
  }

  String actionType() {
    return actionType;
  }

  String traceId() {
    return traceId;
  }

  String fromState() {
    return fromState;
  }

  String toState() {
    return toState;
  }

  Instant createdAt() {
    return createdAt;
  }
}