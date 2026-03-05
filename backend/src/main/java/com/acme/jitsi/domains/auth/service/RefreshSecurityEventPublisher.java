package com.acme.jitsi.domains.auth.service;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class RefreshSecurityEventPublisher {

  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public RefreshSecurityEventPublisher(ApplicationEventPublisher eventPublisher, Clock clock) {
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  void publish(String eventType, String errorCode, String tokenId, String subject, String meetingId) {
    eventPublisher.publishEvent(new AuthRefreshSecurityEvent(
        eventType,
        errorCode,
        tokenId,
        subject,
        meetingId,
        clock.instant()));
  }
}