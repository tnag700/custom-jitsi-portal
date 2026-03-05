package com.acme.jitsi.domains.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StoreSelectionStrategyFactoryTest {

  private final StoreSelectionStrategyFactory factory = new StoreSelectionStrategyFactory();

  @Test
  void resolvesRequestedModeWithDefaultSelectionStrategy() {
    StoreSelectionStrategy<String> strategy = factory.create(
        StoreMode.IN_MEMORY,
        mode -> mode == StoreMode.REDIS ? "redis-store" : "in-memory-store");

    assertThat(strategy.select("redis")).isEqualTo("redis-store");
    assertThat(strategy.select("in-memory")).isEqualTo("in-memory-store");
  }

  @Test
  void usesDefaultModeWhenRawModeIsBlank() {
    StoreSelectionStrategy<String> strategy = factory.create(
        StoreMode.REDIS,
        mode -> mode == StoreMode.REDIS ? "redis-store" : "in-memory-store");

    assertThat(strategy.select(null)).isEqualTo("redis-store");
    assertThat(strategy.select("   ")).isEqualTo("redis-store");
  }

  @Test
  void resolvesFallbackWhenPrimaryStoreIsUnavailable() {
    StoreSelectionStrategy<String> strategy = factory.createWithFallback(
        StoreMode.IN_MEMORY,
        StoreMode.IN_MEMORY,
        mode -> mode == StoreMode.REDIS ? null : "in-memory-store");

    assertThat(strategy.select("redis")).isEqualTo("in-memory-store");
  }

  @Test
  void usesDefaultModeWhenRequestedModeResolvesToNull() {
    StoreSelectionStrategy<String> strategy = factory.create(
        StoreMode.IN_MEMORY,
        mode -> mode == StoreMode.REDIS ? null : "in-memory-store");

    assertThat(strategy.select("redis")).isEqualTo("in-memory-store");
  }

  @Test
  void throwsWhenStrategyCannotResolveRequestedOrFallbackStore() {
    StoreSelectionStrategy<String> strategy = factory.createWithFallback(
        StoreMode.IN_MEMORY,
        StoreMode.IN_MEMORY,
        ignored -> null);

    assertThatThrownBy(() -> strategy.select("redis"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unable to resolve store for mode");
  }
}