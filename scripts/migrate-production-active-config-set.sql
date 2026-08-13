\set ON_ERROR_STOP on

-- Promote exactly one restored active DEV config set into the production JWT
-- contour. The caller supplies a freshly AES-GCM-encrypted production signing
-- secret through a psql variable; plaintext secrets never enter this script.

BEGIN;

LOCK TABLE config_sets IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE production_config_set_migration_target ON COMMIT DROP AS
SELECT
  config_set_id,
  environment_type,
  issuer,
  audience,
  algorithm,
  role_claim,
  jwks_uri,
  access_ttl_minutes,
  refresh_ttl_minutes,
  meetings_service_url,
  updated_at
FROM config_sets
WHERE status = 'ACTIVE'
  AND environment_type = 'DEV'
  AND deleted = false;

SELECT count(*) = 1 AS exactly_one_active_dev
FROM production_config_set_migration_target
\gset

SELECT count(*) = 0 AS no_active_prod
FROM config_sets
WHERE status = 'ACTIVE'
  AND environment_type = 'PROD'
  AND deleted = false
\gset

SELECT count(*) = 1 AS exactly_one_active_total
FROM config_sets
WHERE status = 'ACTIVE'
  AND deleted = false
\gset

\if :exactly_one_active_dev
\else
  \echo 'Expected exactly one active DEV config set; production migration aborted.'
  ROLLBACK;
  \quit 3
\endif

\if :no_active_prod
\else
  \echo 'An active PROD config set already exists; production migration aborted.'
  ROLLBACK;
  \quit 4
\endif

\if :exactly_one_active_total
\else
  \echo 'Expected no additional active config sets; production migration aborted.'
  ROLLBACK;
  \quit 5
\endif

WITH updated AS (
  UPDATE config_sets AS config
  SET
    environment_type = 'PROD',
    issuer = :'production_issuer',
    audience = :'production_audience',
    algorithm = :'production_algorithm',
    role_claim = :'production_role_claim',
    signing_secret_encrypted = :'encrypted_signing_secret',
    jwks_uri = NULL,
    access_ttl_minutes = :'production_access_ttl_minutes',
    refresh_ttl_minutes = :'production_refresh_ttl_minutes',
    meetings_service_url = :'production_meetings_service_url',
    updated_at = CURRENT_TIMESTAMP
  FROM production_config_set_migration_target AS target
  WHERE config.config_set_id = target.config_set_id
    AND config.updated_at = target.updated_at
  RETURNING config.config_set_id
)
SELECT count(*) = 1 AS exactly_one_updated
FROM updated
\gset

\if :exactly_one_updated
\else
  \echo 'Active config set changed concurrently; production migration aborted.'
  ROLLBACK;
  \quit 5
\endif

INSERT INTO config_set_audit_events (
  event_id,
  config_set_id,
  event_type,
  actor_id,
  trace_id,
  changed_fields,
  old_values,
  new_values,
  occurred_at
)
SELECT
  gen_random_uuid()::text,
  target.config_set_id,
  'CONFIG_SET_UPDATED',
  'production-offline-migration',
  :'migration_trace_id',
  'environmentType,issuer,audience,algorithm,roleClaim,signingSecret,jwksUri,accessTtlMinutes,refreshTtlMinutes,meetingsServiceUrl',
  format(
    'environmentType=%s,issuer=%s,audience=%s,algorithm=%s,roleClaim=%s,signingSecret=[HIDDEN],jwksUri=%s,accessTtlMinutes=%s,refreshTtlMinutes=%s,meetingsServiceUrl=%s',
    target.environment_type,
    target.issuer,
    target.audience,
    target.algorithm,
    target.role_claim,
    target.jwks_uri,
    target.access_ttl_minutes,
    target.refresh_ttl_minutes,
    target.meetings_service_url
  ),
  format(
    'environmentType=PROD,issuer=%s,audience=%s,algorithm=%s,roleClaim=%s,signingSecret=[CHANGED],jwksUri=-,accessTtlMinutes=%s,refreshTtlMinutes=%s,meetingsServiceUrl=%s',
    :'production_issuer',
    :'production_audience',
    :'production_algorithm',
    :'production_role_claim',
    :'production_access_ttl_minutes',
    :'production_refresh_ttl_minutes',
    :'production_meetings_service_url'
  ),
  CURRENT_TIMESTAMP
FROM production_config_set_migration_target AS target;

COMMIT;
