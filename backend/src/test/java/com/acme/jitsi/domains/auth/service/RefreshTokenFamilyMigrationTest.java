package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RefreshTokenFamilyMigrationTest {

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void migrationRevokesLegacyStatesAndAdvancesCutoverEvenForAnEmptyStore(boolean existingToken) throws Exception {
    String url = "jdbc:h2:mem:refresh-family-migration-" + UUID.randomUUID() + ";MODE=PostgreSQL";
    try (var connection = DriverManager.getConnection(url, "sa", "");
         var dataSource = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true)) {
      Flyway.configure().dataSource(dataSource).target("21").load().migrate();
      try (var statement = connection.createStatement()) {
        if (existingToken) {
          statement.executeUpdate("""
              INSERT INTO refresh_token_states
                (token_id, subject_id, meeting_id, absolute_expires_at, idle_expires_at, status)
              VALUES ('legacy', 'owner', 'meeting', CURRENT_TIMESTAMP + INTERVAL '1' DAY,
                CURRENT_TIMESTAMP + INTERVAL '1' HOUR, 'USED')
              """);
        }
        Instant migrationStarted = Instant.now().minusSeconds(1);
        Flyway.configure().dataSource(dataSource).load().migrate();
        try (var result = statement.executeQuery("SELECT accept_issued_after FROM refresh_token_store_metadata WHERE singleton_id = 1")) {
          assertThat(result.next()).isTrue();
          assertThat(result.getTimestamp(1).toInstant()).isAfterOrEqualTo(migrationStarted);
        }
        try (var result = statement.executeQuery("SELECT status, family_id FROM refresh_token_states")) {
          assertThat(result.next()).isEqualTo(existingToken);
          if (existingToken) {
            assertThat(result.getString("status")).isEqualTo("REVOKED");
            assertThat(result.getString("family_id")).isEqualTo("legacy");
          }
        }
      }
    }
  }
}
