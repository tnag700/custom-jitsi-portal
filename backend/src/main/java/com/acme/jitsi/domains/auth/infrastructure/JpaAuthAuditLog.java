package com.acme.jitsi.domains.auth.infrastructure;

import com.acme.jitsi.domains.auth.service.AuthAuditLog;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Repository;

@Repository
class JpaAuthAuditLog implements AuthAuditLog {

  private final AuthAuditEventJpaRepository repository;
  private final Clock clock;

  JpaAuthAuditLog(AuthAuditEventJpaRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void record(
      String eventType,
      String actorId,
      String subjectId,
      String meetingId,
      String tokenId,
      String errorCode,
      String traceId,
      String tenantId,
      String clientContext) {
    repository.save(new AuthAuditEventEntity(
        eventType,
        blankToNull(actorId),
        blankToNull(subjectId),
        blankToNull(meetingId),
        blankToNull(tokenId),
        blankToNull(errorCode),
        blankToNull(traceId),
        blankToNull(tenantId),
        blankToNull(clientContext),
        Instant.now(clock)));
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}