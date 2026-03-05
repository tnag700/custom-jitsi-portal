package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.event.ConfigSetActivatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetCompatibilityCheckedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetDeactivatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetRollbackCompletedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetUpdatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class ConfigSetAuditListener {

  private final ConfigSetAuditLog auditLog;
  private final MeterRegistry meterRegistry;

  ConfigSetAuditListener(ConfigSetAuditLog auditLog, MeterRegistry meterRegistry) {
    this.auditLog = auditLog;
    this.meterRegistry = meterRegistry;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCreated(ConfigSetCreatedEvent event) {
    auditLog.record(
        "CONFIG_SET_CREATED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("create");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onUpdated(ConfigSetUpdatedEvent event) {
    auditLog.record(
        "CONFIG_SET_UPDATED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("update");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onActivated(ConfigSetActivatedEvent event) {
    auditLog.record(
        "CONFIG_SET_ACTIVATED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("activate");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onDeactivated(ConfigSetDeactivatedEvent event) {
    auditLog.record(
        "CONFIG_SET_DEACTIVATED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("deactivate");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onRolloutCompleted(ConfigSetRolloutCompletedEvent event) {
    auditLog.record(
        "CONFIG_SET_ROLLOUT_COMPLETED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        "status",
        "",
        event.status());
    recordMetrics("rollout");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onRollbackCompleted(ConfigSetRollbackCompletedEvent event) {
    auditLog.record(
        "CONFIG_SET_ROLLBACK_COMPLETED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        "previousConfigSetId",
        event.previousConfigSetId(),
        event.configSetId());
    recordMetrics("rollback");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onCompatibilityChecked(ConfigSetCompatibilityCheckedEvent event) {
    auditLog.record(
        "CONFIG_SET_COMPATIBILITY_CHECKED",
        event.configSetId(),
        event.actorId(),
        event.traceId(),
        "compatible,mismatchCodes",
        "-",
        "compatible=%s,mismatchCodes=%s".formatted(event.compatible(), String.join(",", event.mismatchCodes())));
    recordMetrics("compatibility-check");
  }

  private void recordMetrics(String action) {
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "configsets",
        "action",
        action).increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "configsets",
        "action",
        action).increment();
  }
}