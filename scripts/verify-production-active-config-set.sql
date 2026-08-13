\set ON_ERROR_STOP on

BEGIN;

CREATE TEMP TABLE verified_active_config ON COMMIT DROP AS
SELECT config_set_id
FROM config_sets
WHERE status = 'ACTIVE'
  AND deleted = false
  AND environment_type = 'PROD'
  AND issuer = 'https://jitsi-mgorka.top'
  AND audience = 'jitsi-meet'
  AND algorithm = 'HS256'
  AND role_claim = 'role'
  AND signing_secret_encrypted IS NOT NULL
  AND length(signing_secret_encrypted) > 32
  AND jwks_uri IS NULL
  AND access_ttl_minutes = 20
  AND refresh_ttl_minutes = 60
  AND meetings_service_url = 'https://jitsi-mgorka.top/api/v1';

SELECT (
  (SELECT count(*) FROM verified_active_config) = 1
  AND (SELECT count(*) FROM config_sets WHERE status = 'ACTIVE' AND deleted = false) = 1
) AS exact_active_config
\gset

SELECT count(*) = 1 AS migration_audited
FROM config_set_audit_events AS audit
JOIN verified_active_config AS config
  ON config.config_set_id = audit.config_set_id
WHERE audit.event_type = 'CONFIG_SET_UPDATED'
  AND audit.actor_id = 'production-offline-migration'
  AND audit.changed_fields LIKE '%signingSecret%'
  AND audit.old_values LIKE '%signingSecret=[HIDDEN]%'
  AND audit.new_values LIKE '%signingSecret=[CHANGED]%'
\gset

SELECT count(*) = 1 AS latest_compatible
FROM (
  SELECT check_result.compatible, check_result.mismatch_codes
  FROM config_set_compatibility_checks AS check_result
  JOIN verified_active_config AS config
    ON config.config_set_id = check_result.config_set_id
  ORDER BY check_result.checked_at DESC
  LIMIT 1
) AS latest
WHERE latest.compatible = true
  AND coalesce(latest.mismatch_codes, '') = ''
\gset

SELECT (
  (SELECT count(*) FROM rooms JOIN verified_active_config AS config USING (config_set_id)) = 2
  AND (SELECT count(*) FROM meetings JOIN verified_active_config AS config USING (config_set_id)) = 1
) AS restored_references_preserved
\gset

\if :exact_active_config
\else
  \echo 'Active production config-set contract is not exact.'
  \quit 3
\endif
\if :migration_audited
\else
  \echo 'Production config-set migration audit is missing.'
  \quit 4
\endif
\if :latest_compatible
\else
  \echo 'Latest production config-set compatibility check is not clean.'
  \quit 5
\endif
\if :restored_references_preserved
\else
  \echo 'Restored room or meeting config-set references changed.'
  \quit 6
\endif

\echo 'Production active config set, audit, compatibility, and restored references are verified.'

ROLLBACK;
