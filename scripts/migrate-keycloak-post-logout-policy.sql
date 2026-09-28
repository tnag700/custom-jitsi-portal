\set ON_ERROR_STOP on

-- Existing realms are not overwritten by --import-realm. Apply the reviewed
-- post-logout policy once, while Keycloak is stopped, with a guarded update.

BEGIN;
LOCK TABLE client_attributes IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE production_backend_client ON COMMIT DROP AS
SELECT client.id
FROM client
JOIN realm ON realm.id = client.realm_id
WHERE realm.name = 'jitsi'
  AND client.client_id = 'jitsi-backend';

SELECT count(*) = 1 AS exactly_one_client
FROM production_backend_client
\gset

\if :exactly_one_client
\else
  \echo 'Expected exactly one jitsi/jitsi-backend client; migration aborted.'
  ROLLBACK;
  DO $$ BEGIN RAISE EXCEPTION 'Expected exactly one jitsi/jitsi-backend client'; END $$;
\endif

INSERT INTO client_attributes (client_id, name, value)
SELECT
  id,
  'post.logout.redirect.uris',
  'https://jitsi-mgorka.top/auth'
FROM production_backend_client
ON CONFLICT (client_id, name)
DO UPDATE SET value = EXCLUDED.value;

INSERT INTO client_attributes (client_id, name, value)
SELECT id, policy.name, policy.value
FROM production_backend_client
CROSS JOIN (VALUES
  ('backchannel.logout.url', 'http://backend:8080/logout/connect/back-channel/keycloak'),
  ('backchannel.logout.session.required', 'true')
) AS policy(name, value)
ON CONFLICT (client_id, name)
DO UPDATE SET value = EXCLUDED.value;

COMMIT;
