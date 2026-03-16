package com.acme.jitsi.domains.auth.infrastructure;

import java.time.Instant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_audit_events")
class AuthAuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "event_type", nullable = false, length = 64)
  private String eventType;

  @Column(name = "actor_id", length = 255)
  private String actorId;

  @Column(name = "subject_id", length = 255)
  private String subjectId;

  @Column(name = "meeting_id", length = 64)
  private String meetingId;

  @Column(name = "token_id", length = 255)
  private String tokenId;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "trace_id", length = 128)
  private String traceId;

  @Column(name = "tenant_id", length = 255)
  private String tenantId;

  @Column(name = "client_context", columnDefinition = "TEXT")
  private String clientContext;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuthAuditEventEntity() {
  }

  AuthAuditEventEntity(
      String eventType,
      String actorId,
      String subjectId,
      String meetingId,
      String tokenId,
      String errorCode,
      String traceId,
      String tenantId,
      String clientContext,
      Instant occurredAt) {
    this.eventType = eventType;
    this.actorId = actorId;
    this.subjectId = subjectId;
    this.meetingId = meetingId;
    this.tokenId = tokenId;
    this.errorCode = errorCode;
    this.traceId = traceId;
    this.tenantId = tenantId;
    this.clientContext = clientContext;
    this.occurredAt = occurredAt;
  }
}