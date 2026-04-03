package com.acme.jitsi.domains.admin.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "admin_incident_coordination_state")
class AdminIncidentCoordinationStateEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "incident_id", nullable = false, length = 64)
  private String incidentId;

  @Column(name = "tenant_id", nullable = false, length = 255)
  private String tenantId;

  @Column(name = "environment", nullable = false, length = 32)
  private String environment;

  @Column(name = "owner", length = 255)
  private String owner;

  @Column(name = "workflow_status", nullable = false, length = 64)
  private String workflowStatus;

  @Column(name = "ticket_reference", length = 255)
  private String ticketReference;

  @Column(name = "ticket_status", nullable = false, length = 64)
  private String ticketStatus;

  @Column(name = "ticket_url", length = 1000)
  private String ticketUrl;

  @Column(name = "updated_by", nullable = false, length = 255)
  private String updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected AdminIncidentCoordinationStateEntity() {
  }

  AdminIncidentCoordinationStateEntity(
      String incidentId,
      String tenantId,
      String environment,
      String owner,
      String workflowStatus,
      String ticketReference,
      String ticketStatus,
      String ticketUrl,
      String updatedBy,
      Instant updatedAt) {
    this.incidentId = incidentId;
    this.tenantId = tenantId;
    this.environment = environment;
    this.owner = owner;
    this.workflowStatus = workflowStatus;
    this.ticketReference = ticketReference;
    this.ticketStatus = ticketStatus;
    this.ticketUrl = ticketUrl;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  Long id() {
    return id;
  }

  String incidentId() {
    return incidentId;
  }

  String tenantId() {
    return tenantId;
  }

  String environment() {
    return environment;
  }

  String owner() {
    return owner;
  }

  String workflowStatus() {
    return workflowStatus;
  }

  String ticketReference() {
    return ticketReference;
  }

  String ticketStatus() {
    return ticketStatus;
  }

  String ticketUrl() {
    return ticketUrl;
  }

  void updateCoordination(String owner, String workflowStatus, String updatedBy, Instant updatedAt) {
    this.owner = owner;
    this.workflowStatus = workflowStatus;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }

  void updateTicketLink(String ticketReference, String ticketStatus, String ticketUrl, String updatedBy, Instant updatedAt) {
    this.ticketReference = ticketReference;
    this.ticketStatus = ticketStatus;
    this.ticketUrl = ticketUrl;
    this.updatedBy = updatedBy;
    this.updatedAt = updatedAt;
  }
}