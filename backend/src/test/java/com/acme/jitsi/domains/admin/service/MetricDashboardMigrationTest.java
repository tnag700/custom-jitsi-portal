package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MetricDashboardMigrationTest {
  @Test
  void defaultH2ModeCanMigrateThePreferenceTableAndItsCompositeKey() throws Exception {
    String url = "jdbc:h2:mem:metrics_default_mode;DB_CLOSE_DELAY=-1";
    Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load().migrate();
    try (var connection = DriverManager.getConnection(url, "sa", "");
         var columns = connection.getMetaData().getColumns(null, null, "METRIC_DASHBOARD_PREFERENCES", "LAYOUT");
         var keys = connection.getMetaData().getPrimaryKeys(null, null, "METRIC_DASHBOARD_PREFERENCES")) {
      assertThat(columns.next()).isTrue();
      assertThat(columns.getString("TYPE_NAME")).isEqualTo("JSON");
      int count = 0; while (keys.next()) count++;
      assertThat(count).isEqualTo(2);
    }
  }
}
