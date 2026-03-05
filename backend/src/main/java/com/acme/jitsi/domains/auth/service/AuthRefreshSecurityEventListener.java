package com.acme.jitsi.domains.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class AuthRefreshSecurityEventListener {

  private static final Logger log = LoggerFactory.getLogger(AuthRefreshSecurityEventListener.class);

  @EventListener
  void onAuthRefreshSecurityEvent(AuthRefreshSecurityEvent event) {
    boolean suspicious = "REFRESH_REUSE".equals(event.eventType()) || "REFRESH_REVOKED".equals(event.eventType());
    if (suspicious) {
      if (log.isWarnEnabled()) {
        log.warn(
            "auth_refresh_security_event eventType={} errorCode={} tokenId={} subject={} meetingId={} occurredAt={}",
            event.eventType(),
            event.errorCode(),
            event.tokenId(),
            event.subject(),
            event.meetingId(),
            event.occurredAt());
      }
      return;
    }

    if (log.isInfoEnabled()) {
      log.info(
          "auth_refresh_security_event eventType={} errorCode={} tokenId={} subject={} meetingId={} occurredAt={}",
          event.eventType(),
          event.errorCode(),
          event.tokenId(),
          event.subject(),
          event.meetingId(),
          event.occurredAt());
    }
  }
}
