package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductionRefreshTokenStoreGuardTest {

  @Test
  void rejectsEveryNonDatabaseStoreMode() {
    for (String mode : new String[] {null, "", "in-memory", "redis", "unexpected"}) {
      AuthRefreshProperties properties = new AuthRefreshProperties();
      properties.setAtomicStore(mode);
      properties.setAcceptIssuedAfter(Instant.parse("2026-08-13T12:00:00Z"));

      assertThatThrownBy(() -> new ProductionRefreshTokenStoreGuard(properties).afterPropertiesSet())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("database");
    }
  }

  @Test
  void acceptsTheDurableDatabaseStoreMode() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("database");
    properties.setAcceptIssuedAfter(Instant.parse("2026-08-13T12:00:00Z"));

    assertThatNoException()
        .isThrownBy(() -> new ProductionRefreshTokenStoreGuard(properties).afterPropertiesSet());
  }

  @Test
  void rejectsMissingDurableStoreCutoverEpoch() {
    AuthRefreshProperties properties = new AuthRefreshProperties();
    properties.setAtomicStore("database");

    assertThatThrownBy(() -> new ProductionRefreshTokenStoreGuard(properties).afterPropertiesSet())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accept-issued-after");
  }
}
