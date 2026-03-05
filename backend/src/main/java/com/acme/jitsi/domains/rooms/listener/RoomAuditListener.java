package com.acme.jitsi.domains.rooms.listener;

import com.acme.jitsi.domains.rooms.event.RoomClosedEvent;
import com.acme.jitsi.domains.rooms.event.RoomCreatedEvent;
import com.acme.jitsi.domains.rooms.event.RoomDeletedEvent;
import com.acme.jitsi.domains.rooms.event.RoomUpdatedEvent;
import com.acme.jitsi.domains.rooms.service.RoomAuditLog;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RoomAuditListener {

  private final RoomAuditLog roomAuditLog;
  private final MeterRegistry meterRegistry;

  public RoomAuditListener(RoomAuditLog roomAuditLog, MeterRegistry meterRegistry) {
    this.roomAuditLog = roomAuditLog;
    this.meterRegistry = meterRegistry;
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleRoomCreatedEvent(RoomCreatedEvent event) {
    roomAuditLog.record(
        "create",
        event.roomId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("create");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleRoomUpdatedEvent(RoomUpdatedEvent event) {
    roomAuditLog.record(
        "update",
        event.roomId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("update");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleRoomClosedEvent(RoomClosedEvent event) {
    roomAuditLog.record(
        "close",
        event.roomId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("close");
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void handleRoomDeletedEvent(RoomDeletedEvent event) {
    roomAuditLog.record(
        "delete",
        event.roomId(),
        event.actorId(),
        event.traceId(),
        event.changedFields(),
        event.oldValues(),
        event.newValues());
    recordMetrics("delete");
  }

  private void recordMetrics(String action) {
    meterRegistry.counter(
        "jitsi.audit.events.total",
        "domain",
        "rooms",
        "action",
        action).increment();
    meterRegistry.counter(
        "jitsi.monitoring.events.total",
        "domain",
        "rooms",
        "action",
        action).increment();
  }
}
