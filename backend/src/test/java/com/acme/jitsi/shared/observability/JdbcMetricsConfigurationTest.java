package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class JdbcMetricsConfigurationTest {

  @Test
  void deniesJdbcMetersButKeepsUnrelatedMetrics() {
    var registry = new SimpleMeterRegistry();
    registry.config().meterFilter(new JdbcMetricsConfiguration().denyUnstableJdbcMeters());

    LongTaskTimer.builder("jdbc.query.active").register(registry);
    Timer.builder("jdbc.query").register(registry);
    LongTaskTimer.builder("application.task.active").register(registry);

    assertThat(registry.find("jdbc.query.active").longTaskTimer()).isNull();
    assertThat(registry.find("jdbc.query").timer()).isNull();
    assertThat(registry.find("application.task.active").longTaskTimer()).isNotNull();
  }
}
