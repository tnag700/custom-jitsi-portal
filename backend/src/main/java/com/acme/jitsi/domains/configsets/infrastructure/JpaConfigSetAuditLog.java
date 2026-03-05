package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetAuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JpaConfigSetAuditLog implements ConfigSetAuditLog {

  private final ConfigSetAuditEventJpaRepository repository;
  private final Clock clock;

  JpaConfigSetAuditLog(ConfigSetAuditEventJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void record(
      String eventType,
      String configSetId,
      String actorId,
      String traceId,
      String changedFields,
      String oldValues,
      String newValues) {
    repository.save(new ConfigSetAuditEventEntity(
        UUID.randomUUID().toString(),
        configSetId,
        eventType,
        actorId,
        traceId,
        changedFields,
        oldValues,
        newValues,
        Instant.now(clock)));
  }
}