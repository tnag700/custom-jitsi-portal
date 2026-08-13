\set ON_ERROR_STOP on

SELECT count(*) = 1 AS exact_policy
FROM client_attributes AS attribute
JOIN client ON client.id = attribute.client_id
JOIN realm ON realm.id = client.realm_id
WHERE realm.name = 'jitsi'
  AND client.client_id = 'jitsi-backend'
  AND attribute.name = 'post.logout.redirect.uris'
  AND attribute.value = 'https://jitsi-mgorka.top/auth'
\gset

\if :exact_policy
  \echo 'Keycloak post-logout policy is exact.'
\else
  \echo 'Keycloak post-logout policy is missing or ambiguous.'
  \quit 3
\endif
