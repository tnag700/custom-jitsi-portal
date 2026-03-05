package com.acme.jitsi.domains.configsets.infrastructure;

import static org.mockito.Mockito.verify;

import com.acme.jitsi.domains.configsets.event.ConfigSetActivatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetCompatibilityCheckedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetCreatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetDeactivatedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetRollbackCompletedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent;
import com.acme.jitsi.domains.configsets.event.ConfigSetUpdatedEvent;
import com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigSetAuditListenerTest {

  @Mock
  private ConfigSetAuditLog auditLog;

  private ConfigSetAuditListener listener;

  @BeforeEach
  void setUp() {
    listener = new ConfigSetAuditListener(auditLog, new SimpleMeterRegistry());
  }

  @Test
  void handlesCreatedEvent() {
    listener.onCreated(new ConfigSetCreatedEvent("cs-1", "actor", "trace", "f", "o", "n"));
    verify(auditLog).record("CONFIG_SET_CREATED", "cs-1", "actor", "trace", "f", "o", "n");
  }

  @Test
  void handlesUpdatedEvent() {
    listener.onUpdated(new ConfigSetUpdatedEvent("cs-1", "actor", "trace", "f", "o", "n"));
    verify(auditLog).record("CONFIG_SET_UPDATED", "cs-1", "actor", "trace", "f", "o", "n");
  }

  @Test
  void handlesActivatedEvent() {
    listener.onActivated(new ConfigSetActivatedEvent("cs-1", "actor", "trace", "f", "o", "n"));
    verify(auditLog).record("CONFIG_SET_ACTIVATED", "cs-1", "actor", "trace", "f", "o", "n");
  }

  @Test
  void handlesDeactivatedEvent() {
    listener.onDeactivated(new ConfigSetDeactivatedEvent("cs-1", "actor", "trace", "f", "o", "n"));
    verify(auditLog).record("CONFIG_SET_DEACTIVATED", "cs-1", "actor", "trace", "f", "o", "n");
  }

  @Test
  void handlesRolloutCompletedEvent() {
    listener.onRolloutCompleted(new ConfigSetRolloutCompletedEvent(
        "r-1",
        "cs-1",
        "cs-0",
        "actor",
        "trace",
        "SUCCEEDED"));
    verify(auditLog).record("CONFIG_SET_ROLLOUT_COMPLETED", "cs-1", "actor", "trace", "status", "", "SUCCEEDED");
  }

  @Test
  void handlesRollbackCompletedEvent() {
    listener.onRollbackCompleted(new ConfigSetRollbackCompletedEvent(
        "r-2",
        "cs-0",
        "cs-1",
        "actor",
        "trace"));
    verify(auditLog).record(
        "CONFIG_SET_ROLLBACK_COMPLETED",
        "cs-0",
        "actor",
        "trace",
        "previousConfigSetId",
        "cs-1",
        "cs-0");
  }

  @Test
  void handlesCompatibilityCheckedEvent() {
    listener.onCompatibilityChecked(new ConfigSetCompatibilityCheckedEvent(
        "cs-1",
        "actor",
        "trace",
        false,
        java.util.List.of("ISSUER_MISMATCH", "API_VERSION_MISMATCH"),
        "issuer mismatch; version mismatch"));

    verify(auditLog).record(
        "CONFIG_SET_COMPATIBILITY_CHECKED",
        "cs-1",
        "actor",
        "trace",
        "compatible,mismatchCodes",
        "-",
        "compatible=false,mismatchCodes=ISSUER_MISMATCH,API_VERSION_MISMATCH");
  }
}