package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FlowObservationFacadeTest {

  @Test
  void lowRejectsUnsupportedObservationKeys() {
    FlowObservationFacade facade = new FlowObservationFacade(RecordableObservationRegistryFactory.create());

    assertThatThrownBy(() -> facade.observe(
      "test.flow",
      (FlowObservationFacade.ObservationRunnable) observation -> observation.low("subject", "u-host")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported low-cardinality observation key: subject");
  }

  private static final class RecordableObservationRegistryFactory {

    private static io.micrometer.observation.ObservationRegistry create() {
      return io.micrometer.observation.ObservationRegistry.create();
    }
  }
}