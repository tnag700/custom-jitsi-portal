package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V26__Create_metric_dashboard_preferences extends BaseJavaMigration {
  @Override
  public void migrate(Context context) throws Exception {
    boolean postgres = "PostgreSQL".equals(context.getConnection().getMetaData().getDatabaseProductName());
    String layoutType = postgres ? "JSONB CHECK (jsonb_typeof(layout) = 'object')" : "JSON";
    try (var statement = context.getConnection().createStatement()) {
      statement.execute("""
          CREATE TABLE metric_dashboard_preferences (
              tenant_id TEXT NOT NULL,
              subject_id TEXT NOT NULL,
              revision BIGINT NOT NULL CHECK (revision >= 1),
              layout %s NOT NULL,
              updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (tenant_id, subject_id)
          )
          """.formatted(layoutType));
    }
  }
}
