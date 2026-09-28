package com.acme.jitsi.domains.auth.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

/** Runs refresh family races and expiry checks against native PostgreSQL row locks. */
@Tag("container")
@EnabledIfEnvironmentVariable(named = "JITSI_TEST_POSTGRES_URL", matches = "jdbc:postgresql:.*")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestExecutionListeners(listeners = RefreshTokenStoreNativePostgresIntegrationTest.SchemaCleanup.class,
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
class RefreshTokenStoreNativePostgresIntegrationTest extends DatabaseRefreshTokenStoreIntegrationTest {
  private static final String SCHEMA = "jitsi_test_" + UUID.randomUUID().toString().replace("-", "");

  @DynamicPropertySource
  static void postgres(DynamicPropertyRegistry registry) throws SQLException {
    execute("CREATE SCHEMA " + SCHEMA);
    String url = System.getenv("JITSI_TEST_POSTGRES_URL");
    registry.add("spring.datasource.url", () -> url + (url.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
    registry.add("spring.datasource.username", () -> System.getenv("JITSI_TEST_POSTGRES_USER"));
    registry.add("spring.datasource.password", () -> System.getenv("JITSI_TEST_POSTGRES_PASSWORD"));
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  public static class SchemaCleanup extends AbstractTestExecutionListener {
    @Override
    public int getOrder() { return Integer.MIN_VALUE; }

    @Override
    public void afterTestClass(TestContext ignored) throws SQLException {
      // afterTestClass runs in reverse order: drop only after DirtiesContext closes the pool.
      execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }
  }

  private static void execute(String sql) throws SQLException {
    try (var connection = DriverManager.getConnection(System.getenv("JITSI_TEST_POSTGRES_URL"),
        System.getenv("JITSI_TEST_POSTGRES_USER"), System.getenv("JITSI_TEST_POSTGRES_PASSWORD"));
        var statement = connection.createStatement()) {
      statement.execute(sql);
    }
  }
}
