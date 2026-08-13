package com.acme.jitsi.domains.auth.service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("prod")
@DependsOn("flywayInitializer")
final class ProductionRefreshTokenCutoverGuard implements InitializingBean {

  private final AuthRefreshProperties properties;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  ProductionRefreshTokenCutoverGuard(
      AuthRefreshProperties properties,
      JdbcTemplate jdbcTemplate,
      TransactionTemplate transactionTemplate) {
    this.properties = properties;
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  public void afterPropertiesSet() {
    Instant configuredCutover = properties.acceptIssuedAfter();
    if (configuredCutover == null) {
      throw new IllegalStateException(
          "Production refresh-token replay protection requires app.auth.refresh.accept-issued-after.");
    }
    transactionTemplate.executeWithoutResult(status -> enforceMonotonicCutover(configuredCutover));
  }

  private void enforceMonotonicCutover(Instant configuredCutover) {
    List<Instant> persistedCutovers = jdbcTemplate.query(
        """
            SELECT accept_issued_after
            FROM refresh_token_store_metadata
            WHERE singleton_id = 1
            FOR UPDATE
            """,
        (resultSet, rowNumber) -> resultSet.getTimestamp("accept_issued_after").toInstant());

    if (persistedCutovers.size() != 1) {
      throw new IllegalStateException(
          "Production refresh-token cutover metadata singleton is missing or duplicated.");
    }

    Instant persistedCutover = persistedCutovers.getFirst();
    if (configuredCutover.isBefore(persistedCutover)) {
      throw new IllegalStateException(
          "Production refresh-token cutover cannot move backward from "
              + persistedCutover + " to " + configuredCutover + '.');
    }
    if (configuredCutover.isAfter(persistedCutover)) {
      jdbcTemplate.update(
          """
              UPDATE refresh_token_store_metadata
              SET accept_issued_after = ?, updated_at = CURRENT_TIMESTAMP
              WHERE singleton_id = 1
              """,
          Timestamp.from(configuredCutover));
    }
  }
}
