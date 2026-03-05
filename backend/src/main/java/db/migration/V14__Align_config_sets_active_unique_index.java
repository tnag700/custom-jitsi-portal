package db.migration;

import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V14__Align_config_sets_active_unique_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    String databaseProduct = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);

    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP INDEX IF EXISTS uq_config_sets_active_env_tenant");
      statement.execute("DROP INDEX IF EXISTS uq_config_sets_env_tenant_status_deleted");

      if (databaseProduct.contains("postgresql")) {
        statement.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_config_sets_active_env_tenant
              ON config_sets(environment_type, tenant_id)
              WHERE status = 'ACTIVE' AND deleted = false
            """);
      } else if (databaseProduct.contains("h2")) {
        statement.execute("""
            ALTER TABLE config_sets
            ADD COLUMN IF NOT EXISTS active_env_tenant_key VARCHAR(600)
            AS (CASE WHEN status = 'ACTIVE' AND deleted = false
                     THEN CONCAT(environment_type, '|', tenant_id)
                     ELSE NULL END)
            """);
        statement.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_config_sets_active_env_tenant
              ON config_sets(active_env_tenant_key)
            """);
      } else {
        statement.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uq_config_sets_env_tenant_status_deleted
              ON config_sets(environment_type, tenant_id, status, deleted)
            """);
      }
    }
  }
}
