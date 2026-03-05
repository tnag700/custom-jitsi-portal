package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtStartupValidationBootstrapTest {

  @Test
  void publishesPassedEventWhenValidationSucceeds() {
    RecordingReporter reporter = new RecordingReporter();
    JwtStartupValidationBootstrap bootstrap = new JwtStartupValidationBootstrap(() -> {
    }, reporter);

    bootstrap.afterPropertiesSet();

    assertThat(reporter.events).hasSize(1);
    JwtStartupValidationEvent event = reporter.events.getFirst();
    assertThat(event.eventType()).isEqualTo(JwtStartupValidationEventType.CONFIG_VALIDATION_PASSED);
    assertThat(event.errorCode()).isEqualTo(JwtStartupValidationErrorCode.NONE);
    assertThat(event.message()).contains("contour is valid");
    assertThat(event.occurredAt()).isBeforeOrEqualTo(Instant.now());
    assertThat(event.correlationId()).isNotBlank();
  }

  @Test
  void publishesFailedEventAndRethrowsWhenValidationFails() {
    RecordingReporter reporter = new RecordingReporter();
    JwtStartupValidationBootstrap bootstrap = new JwtStartupValidationBootstrap(
        () -> {
          throw new JwtStartupValidationException(
              JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE,
              "invalid");
        },
        reporter);

    assertThatThrownBy(bootstrap::afterPropertiesSet)
        .isInstanceOf(JwtStartupValidationException.class)
        .extracting(error -> ((JwtStartupValidationException) error).errorCode())
        .isEqualTo("CONFIG_INCOMPATIBLE");

    assertThat(reporter.events).hasSize(1);
    JwtStartupValidationEvent event = reporter.events.getFirst();
    assertThat(event.eventType()).isEqualTo(JwtStartupValidationEventType.CONFIG_VALIDATION_FAILED);
    assertThat(event.errorCode()).isEqualTo(JwtStartupValidationErrorCode.CONFIG_INCOMPATIBLE);
    assertThat(event.correlationId()).isNotBlank();
  }

  private static final class RecordingReporter implements JwtStartupValidationReporter {

    private final List<JwtStartupValidationEvent> events = new ArrayList<>();

    @Override
    public void report(JwtStartupValidationEvent event) {
      events.add(event);
    }
  }
}
