package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class RefreshSecurityEventPublisherTest {

  @Test
  void publishesAuthRefreshSecurityEventWithGivenFields() {
    ApplicationEventPublisher springEventPublisher = mock(ApplicationEventPublisher.class);
    Instant fixedNow = Instant.parse("2026-02-16T10:10:00Z");
    Clock fixedClock = Clock.fixed(fixedNow, ZoneOffset.UTC);
    RefreshSecurityEventPublisher publisher = new RefreshSecurityEventPublisher(springEventPublisher, fixedClock);

    publisher.publish("REFRESH_REUSE", "REFRESH_REUSE_DETECTED", "reuse-jti-1", "u-host", "meeting-a");

    ArgumentCaptor<AuthRefreshSecurityEvent> captor = ArgumentCaptor.forClass(AuthRefreshSecurityEvent.class);
    verify(springEventPublisher).publishEvent(captor.capture());

    AuthRefreshSecurityEvent event = captor.getValue();
    assertThat(event.eventType()).isEqualTo("REFRESH_REUSE");
    assertThat(event.errorCode()).isEqualTo("REFRESH_REUSE_DETECTED");
    assertThat(event.tokenId()).isEqualTo("reuse-jti-1");
    assertThat(event.subject()).isEqualTo("u-host");
    assertThat(event.meetingId()).isEqualTo("meeting-a");
    assertThat(event.occurredAt()).isEqualTo(fixedNow);
  }
}