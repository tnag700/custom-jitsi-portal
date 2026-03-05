package db.migration;

import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V10__Add_single_host_partial_index extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    String databaseProduct = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);

    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute("DROP INDEX IF EXISTS uk_meeting_single_host");

      statement.execute("""
          DELETE FROM meeting_participant_assignments
          WHERE assignment_id IN (
              SELECT assignment_id
              FROM (
                  SELECT assignment_id,
                         ROW_NUMBER() OVER (
                             PARTITION BY meeting_id
                             ORDER BY assigned_at ASC, created_at ASC, assignment_id ASC
                         ) AS rn
                  FROM meeting_participant_assignments
                  WHERE role = 'host'
              ) ranked_hosts
              WHERE rn > 1
          )
          """);

      if (databaseProduct.contains("postgresql")) {
        statement.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uk_meeting_single_host
                ON meeting_participant_assignments(meeting_id)
                WHERE role = 'host'
            """);
        } else if (databaseProduct.contains("h2")) {
        statement.execute("""
          ALTER TABLE meeting_participant_assignments
          ADD COLUMN IF NOT EXISTS host_meeting_id VARCHAR(255)
          AS (CASE WHEN role = 'host' THEN meeting_id ELSE NULL END)
          """);

        statement.execute("""
          CREATE UNIQUE INDEX IF NOT EXISTS uk_meeting_single_host
            ON meeting_participant_assignments(host_meeting_id)
          """);
      } else {
        statement.execute("""
          CREATE INDEX IF NOT EXISTS uk_meeting_single_host
            ON meeting_participant_assignments(meeting_id)
            """);
      }
    }
  }
}