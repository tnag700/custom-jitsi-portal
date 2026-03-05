package com.acme.jitsi.domains.rooms.infrastructure;

import com.acme.jitsi.domains.rooms.service.RoomAuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
class Slf4jRoomAuditLog implements RoomAuditLog {

  private static final Logger log = LoggerFactory.getLogger(Slf4jRoomAuditLog.class);

  @Override
  public void record(
      String actionType,
      String roomId,
      String actorId,
      String traceId,
      String changedFields,
      String oldValues,
      String newValues) {
    log.info(
        "room_audit action={} roomId={} actorId={} traceId={} changedFields={} oldValues={} newValues={}",
        actionType,
        roomId,
        actorId,
        traceId,
        changedFields,
        oldValues,
        newValues);
  }
}