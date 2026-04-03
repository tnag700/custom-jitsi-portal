package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentCoordinationPort;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.features.admin-incident-coordination", havingValue = "true")
class AdminIncidentCoordinationJpaAdapter implements AdminIncidentCoordinationPort {

  private static final String DEFAULT_WORKFLOW_STATUS = "triage";
  private static final String DEFAULT_TICKET_STATUS = "not-linked";

  private final AdminIncidentCoordinationStateJpaRepository stateRepository;
  private final AdminIncidentCoordinationAuditEventJpaRepository auditRepository;
  private final Clock clock;

  AdminIncidentCoordinationJpaAdapter(
      AdminIncidentCoordinationStateJpaRepository stateRepository,
      AdminIncidentCoordinationAuditEventJpaRepository auditRepository,
      Clock clock) {
    this.stateRepository = stateRepository;
    this.auditRepository = auditRepository;
    this.clock = clock;
  }

  @Override
  public CoordinationSnapshot describe(CoordinationContext context) {
    AdminIncidentCoordinationStateEntity state = loadState(context).orElse(null);
    List<CoordinationAuditEntry> history = auditRepository
        .findTop5ByIncidentIdAndTenantIdAndEnvironmentOrderByCreatedAtDescIdDesc(
            context.incidentId(),
            context.tenantId(),
            context.environment())
        .stream()
        .map(entry -> new CoordinationAuditEntry(
            entry.createdAt().toString(),
            entry.actorId(),
            entry.actionType(),
            entry.traceId(),
            entry.fromState(),
            entry.toState()))
        .toList();
    return new CoordinationSnapshot(
        true,
        "available",
        "Coordination seam остаётся лёгким поверх investigation workspace и не расширяет очередь до workflow board.",
        state == null ? null : state.owner(),
        state == null ? DEFAULT_WORKFLOW_STATUS : state.workflowStatus(),
        state == null ? null : state.ticketReference(),
        state == null ? DEFAULT_TICKET_STATUS : state.ticketStatus(),
        state == null ? null : state.ticketUrl(),
        history);
  }

  @Override
  public CoordinationSnapshot update(CoordinationUpdateCommand command) {
    AdminIncidentCoordinationStateEntity state = loadOrCreate(command.context(), command.actorId());
    Instant now = Instant.now(clock);
    String nextOwner = normalizeOptional(command.owner());
    String nextWorkflowStatus = command.workflowStatus();
    String nextTicketReference = normalizeOptional(command.ticketReference());
    String nextTicketStatus = normalizeOptional(command.ticketStatus());
    boolean ticketUpdateRequested = command.ticketReference() != null || command.ticketStatus() != null;
    String resolvedTicketStatus = nextTicketStatus == null ? DEFAULT_TICKET_STATUS : nextTicketStatus;
    String fromState = summarize(state);
    boolean coordinationChanged = !Objects.equals(state.owner(), nextOwner)
        || !Objects.equals(state.workflowStatus(), nextWorkflowStatus);
    boolean ticketChanged = ticketUpdateRequested && (!Objects.equals(state.ticketReference(), nextTicketReference)
        || !Objects.equals(state.ticketStatus(), resolvedTicketStatus)
        || state.ticketUrl() != null);
    boolean changed = coordinationChanged || ticketChanged;
    if (changed) {
      state.updateCoordination(nextOwner, nextWorkflowStatus, command.actorId(), now);
      if (ticketUpdateRequested) {
        state.updateTicketLink(nextTicketReference, resolvedTicketStatus, null, command.actorId(), now);
      }
      stateRepository.save(state);
      auditRepository.save(new AdminIncidentCoordinationAuditEventEntity(
          state.incidentId(),
          state.tenantId(),
          state.environment(),
          command.actorId(),
          "coordination-updated",
          normalizeOptional(command.traceId()),
          fromState,
          summarize(state),
          now));
    }
    return describe(command.context());
  }

  @Override
  public CoordinationSnapshot recordTicketLink(TicketLinkCommand command) {
    if (normalizeOptional(command.ticketReference()) == null && normalizeOptional(command.ticketUrl()) == null) {
      return describe(command.context());
    }
    AdminIncidentCoordinationStateEntity state = loadOrCreate(command.context(), command.actorId());
    Instant now = Instant.now(clock);
    String nextTicketReference = normalizeOptional(command.ticketReference());
    String nextTicketStatus = normalizeOptional(command.ticketStatus());
    String nextTicketUrl = normalizeOptional(command.ticketUrl());
    String fromState = summarize(state);
    boolean changed = !Objects.equals(state.ticketReference(), nextTicketReference)
        || !Objects.equals(state.ticketStatus(), nextTicketStatus)
        || !Objects.equals(state.ticketUrl(), nextTicketUrl);
    if (changed) {
      state.updateTicketLink(nextTicketReference, nextTicketStatus == null ? DEFAULT_TICKET_STATUS : nextTicketStatus, nextTicketUrl, command.actorId(), now);
      stateRepository.save(state);
      auditRepository.save(new AdminIncidentCoordinationAuditEventEntity(
          state.incidentId(),
          state.tenantId(),
          state.environment(),
          command.actorId(),
          "ticket-linked",
          normalizeOptional(command.traceId()),
          fromState,
          summarize(state),
          now));
    }
    return describe(command.context());
  }

  private java.util.Optional<AdminIncidentCoordinationStateEntity> loadState(CoordinationContext context) {
    return stateRepository.findByIncidentIdAndTenantIdAndEnvironment(
        context.incidentId(),
        context.tenantId(),
        context.environment());
  }

  private AdminIncidentCoordinationStateEntity loadOrCreate(CoordinationContext context, String actorId) {
    return loadState(context).orElseGet(() -> new AdminIncidentCoordinationStateEntity(
        context.incidentId(),
        context.tenantId(),
        context.environment(),
        null,
        DEFAULT_WORKFLOW_STATUS,
        null,
        DEFAULT_TICKET_STATUS,
        null,
        actorId,
        Instant.now(clock)));
  }

  private String summarize(AdminIncidentCoordinationStateEntity state) {
    return "owner=%s; workflowStatus=%s; ticketReference=%s; ticketStatus=%s".formatted(
        valueOrPlaceholder(state.owner()),
        valueOrPlaceholder(state.workflowStatus()),
        valueOrPlaceholder(state.ticketReference()),
        valueOrPlaceholder(state.ticketStatus()));
  }

  private String valueOrPlaceholder(String value) {
    String normalized = normalizeOptional(value);
    return normalized == null ? "<none>" : normalized;
  }

  private String normalizeOptional(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}