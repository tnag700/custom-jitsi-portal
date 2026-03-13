package com.acme.jitsi.shared.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowObservationFacade {

  private final ObservationRegistry observationRegistry;
  private final boolean noop;

  @Autowired
  public FlowObservationFacade(ObservationRegistry observationRegistry) {
    this(observationRegistry, false);
  }

  private FlowObservationFacade(ObservationRegistry observationRegistry, boolean noop) {
    this.observationRegistry = observationRegistry;
    this.noop = noop;
  }

  public static FlowObservationFacade noop() {
    return new FlowObservationFacade(ObservationRegistry.create(), true);
  }

  public <T> T observe(String name, ObservationSupplier<T> supplier) {
    FlowObservation flowObservation = new FlowObservation(this);
    if (noop) {
      return supplier.get(flowObservation);
    }

    Observation observation = Observation.start(name, observationRegistry);
    try (Observation.Scope scope = observation.openScope()) {
      return supplier.get(flowObservation);
    } catch (RuntimeException ex) {
      observation.error(ex);
      throw ex;
    } finally {
      flowObservation.applyTo(observation);
      observation.stop();
    }
  }

  public void observe(String name, ObservationRunnable runnable) {
    observe(name, flowObservation -> {
      runnable.run(flowObservation);
      return null;
    });
  }

  @FunctionalInterface
  public interface ObservationSupplier<T> {
    T get(FlowObservation flowObservation);
  }

  @FunctionalInterface
  public interface ObservationRunnable {
    void run(FlowObservation flowObservation);
  }

  public static final class FlowObservation {

    private static final Set<String> ALLOWED_LOW_CARDINALITY_KEYS = Set.of(
        "flow.outcome",
        "flow.stage",
        "flow.retry_path",
        "flow.rollback",
        "flow.store",
        "flow.guest",
        "flow.compatibility");

    private final FlowObservationFacade facade;
    private final Map<String, String> lowCardinalityValues = new LinkedHashMap<>();

    private FlowObservation(FlowObservationFacade facade) {
      this.facade = facade;
    }

    public FlowObservation outcome(String value) {
      return low("flow.outcome", value);
    }

    public FlowObservation stage(String value) {
      return low("flow.stage", value);
    }

    public FlowObservation retryPath(String value) {
      return low("flow.retry_path", value);
    }

    public FlowObservation rollback(String value) {
      return low("flow.rollback", value);
    }

    public FlowObservation store(String value) {
      return low("flow.store", value);
    }

    public FlowObservation guest(boolean value) {
      return low("flow.guest", Boolean.toString(value));
    }

    public FlowObservation compatibility(String value) {
      return low("flow.compatibility", value);
    }

    public FlowObservation low(String key, String value) {
      if (!ALLOWED_LOW_CARDINALITY_KEYS.contains(key)) {
        throw new IllegalArgumentException("Unsupported low-cardinality observation key: " + key);
      }
      if (value != null && !value.isBlank()) {
        lowCardinalityValues.put(key, value);
      }
      return this;
    }

    private void applyTo(Observation observation) {
      lowCardinalityValues.forEach((key, value) -> observation.lowCardinalityKeyValue(KeyValue.of(key, value)));
    }
  }
}