package com.acme.jitsi.domains.store;

import java.util.Objects;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class StoreSelectionStrategyFactory {

  public <T> StoreSelectionStrategy<T> create(
      StoreMode defaultMode,
      Function<StoreMode, T> resolver) {
    Objects.requireNonNull(defaultMode, "defaultMode must not be null");
    Objects.requireNonNull(resolver, "resolver must not be null");

    return rawMode -> {
      StoreMode requestedMode = StoreMode.fromRaw(rawMode, defaultMode);
      T selectedStore = resolver.apply(requestedMode);
      if (selectedStore != null) {
        return selectedStore;
      }
      return requireResolvedStore(resolver.apply(defaultMode), defaultMode);
    };
  }

  public <T> StoreSelectionStrategy<T> createWithFallback(
      StoreMode defaultMode,
      StoreMode fallbackMode,
      Function<StoreMode, T> resolver) {
    Objects.requireNonNull(defaultMode, "defaultMode must not be null");
    Objects.requireNonNull(fallbackMode, "fallbackMode must not be null");
    Objects.requireNonNull(resolver, "resolver must not be null");

    return rawMode -> {
      StoreMode requestedMode = StoreMode.fromRaw(rawMode, defaultMode);
      T selectedStore = resolver.apply(requestedMode);
      if (selectedStore != null) {
        return selectedStore;
      }

      T fallbackStore = resolver.apply(fallbackMode);
      if (fallbackStore != null) {
        return fallbackStore;
      }

      return requireResolvedStore(resolver.apply(defaultMode), defaultMode);
    };
  }

  private <T> T requireResolvedStore(T store, StoreMode mode) {
    if (store == null) {
      throw new IllegalStateException("Unable to resolve store for mode: " + mode);
    }
    return store;
  }
}