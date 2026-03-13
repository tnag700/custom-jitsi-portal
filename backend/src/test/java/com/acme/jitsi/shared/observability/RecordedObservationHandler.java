package com.acme.jitsi.shared.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RecordedObservationHandler implements ObservationHandler<Observation.Context> {

  private final List<RecordedObservation> completed = new CopyOnWriteArrayList<>();

  public ObservationRegistry createRegistry() {
    ObservationRegistry registry = ObservationRegistry.create();
    registry.observationConfig().observationHandler(this);
    return registry;
  }

  public void reset() {
    completed.clear();
  }

  public List<RecordedObservation> completed() {
    return List.copyOf(completed);
  }

  public RecordedObservation only(String name) {
    return completed.stream()
        .filter(observation -> observation.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Observation not found: " + name + ", completed=" + completed));
  }

  @Override
  public void onStop(Observation.Context context) {
    Map<String, String> lowCardinality = new LinkedHashMap<>();
    for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
      lowCardinality.put(keyValue.getKey(), keyValue.getValue());
    }
    completed.add(new RecordedObservation(context.getName(), lowCardinality, context.getError() != null));
  }

  @Override
  public boolean supportsContext(Observation.Context context) {
    return true;
  }

  public record RecordedObservation(String name, Map<String, String> lowCardinality, boolean error) {
  }
}