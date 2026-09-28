\set ON_ERROR_STOP on

SELECT count(*) = 3 AS exact_policy
FROM client_attributes AS attribute
JOIN client ON client.id = attribute.client_id
JOIN realm ON realm.id = client.realm_id
WHERE realm.name = 'jitsi'
  AND client.client_id = 'jitsi-backend'
  AND (attribute.name, attribute.value) IN (
    ('post.logout.redirect.uris', 'https://jitsi-mgorka.top/auth'),
    ('backchannel.logout.url', 'http://backend:8080/logout/connect/back-channel/keycloak'),
    ('backchannel.logout.session.required', 'true')
  )
\gset

\if :exact_policy
  \echo 'Keycloak post-logout policy is exact.'
\else
  \echo 'Keycloak post-logout policy is missing or ambiguous.'
  DO $$ BEGIN RAISE EXCEPTION 'Keycloak logout policy is missing or ambiguous'; END $$;
\endif
