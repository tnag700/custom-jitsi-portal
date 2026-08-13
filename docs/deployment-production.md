# Production deployment

This guide prepares the portal for a hostname-based deployment on one Ubuntu 24
host. The canonical public names are `jitsi-mgorka.top`,
`auth.jitsi-mgorka.top`, and `meet.jitsi-mgorka.top`.

The canonical public perimeter is intentionally small:

- TCP 80 and 443 terminate on Nginx.
- UDP 10000 is forwarded directly to Jitsi Videobridge.
- PostgreSQL, Redis, backend, frontend, Keycloak, Vault, Prometheus,
  Alertmanager, Grafana, and their management endpoints are not published.

## DNS and router prerequisites

Create public DNS A records for all three hostnames pointing to
`86.57.222.216`. If the ISP equipment and the site router both perform NAT,
configure forwarding on both devices or switch the ISP device to bridge mode.

Forward these ports to the application VM:

| External port | Protocol | VM target | Purpose |
| --- | --- | --- | --- |
| 80 | TCP | `10.10.100.29:80` | ACME challenge and HTTPS redirect |
| 443 | TCP | `10.10.100.29:443` | Portal, Keycloak, and Jitsi web traffic |
| 10000 | UDP | `10.10.100.29:10000` | Jitsi media |

Do not forward SSH globally. Restrict TCP 22 to an operator VPN or a known
source CIDR. Test UDP 10000 from a genuinely external network; a TCP port
scanner cannot validate the media path. This deployment uses split-horizon ICE
candidates: `JVB_ADVERTISE_IPS=10.10.100.29,86.57.222.216`. Internal clients
can reach the VM directly on UDP 10000, while external clients use the public
NAT address. Keep the public address in the list and allow UDP 10000 from every
authorized LAN/VPN segment to the VM.

Prefer split-horizon DNS for browser traffic: internal resolvers return
`10.10.100.29` for the portal, auth and meet hostnames, while public DNS keeps
returning `86.57.222.216`. If internal DNS cannot be changed, configure NAT
reflection for TCP 80/443 and UDP 10000 instead.

## Repository preparation

1. Install Docker Engine with the Compose plugin and Git.
2. Clone the repository onto a filesystem with at least 20 GiB free before
   pulling images and building both applications.
3. Copy `.env.production.example` to `.env.production`.
4. Verify the committed domain values and public NAT address against the live
   router before every cutover.
5. Keep secret values out of `.env.production`; repo-managed env files contain
   only non-secret config и path hints to private, Vault-rendered delivery
   files.
6. Run `npm run prod:operator:prepare`. It generates coherent service files,
   an internal Vault CA/server certificate and `.env.production` under the
   ignored operator directory without printing any secret values. The CA
   signing key is isolated under `deploy/production/local/custody/vault-ca/`;
   it is never placed in the runtime-mounted Vault TLS directory.

Before any deployment:

```bash
npm run stack:validate
npm run prod:baseline:validate
npm run prod:host:baseline:validate
docker compose --project-name jitsi-prod --env-file .env.production \
  -f docker-compose.production.yml config >/tmp/jitsi-compose.yml
```

The configuration render must show only `80/tcp`, `443/tcp`, and
`10000/udp` as host-published ports. It must also contain a non-empty
`JVB_ADVERTISE_IPS`.

## Certificates

Obtain one trusted certificate containing all three hostnames before starting
the hardened Nginx service. The default certificate name is
`jitsi-mgorka.top`. `NGINX_CERTS_PATH` points to the operator-managed Certbot
tree. A network-isolated one-shot `nginx-cert-bootstrap` service copies only
`fullchain.pem` and `privkey.pem` into the private `nginx-certs` volume, assigns
them to the rootless Nginx UID/GID 101, and then exits. The long-lived edge
container never mounts the host Certbot tree.

```bash
sudo certbot certonly --standalone \
  -d jitsi-mgorka.top \
  -d auth.jitsi-mgorka.top \
  -d meet.jitsi-mgorka.top
```

The command requires public TCP 80 forwarding to the VM. Do not substitute a
self-signed or expired edge certificate for the public cutover.

If DNS-01 is used manually before TCP 80 is forwarded, the resulting
certificate has no unattended renewal. Before its expiry, automate DNS-01 with
an operator-managed DNS API credential or switch to a tested HTTP-01 renewal
path. After every successful renewal, refresh only the certificate handoff and
then recreate the edge container:

```bash
docker compose --env-file .env.production -f docker-compose.production.yml \
  up --no-deps --force-recreate nginx-cert-bootstrap
docker compose --env-file .env.production -f docker-compose.production.yml \
  up -d --no-deps --force-recreate nginx
```

Require `nginx-cert-bootstrap` to exit with code 0 and run `nginx -t` plus the
trusted-TLS smoke checks before accepting the refreshed edge.

```bash
docker compose --env-file .env.production -f docker-compose.production.yml \
  exec -T nginx nginx -t
```

The `deploy/nginx/portal-ip.conf.example` file is a bootstrap-only fallback. It
does not provide the hostname-separated Keycloak and Jitsi production policy
and is not the production source of truth.

## Keycloak

Production imports a private realm copy from `KEYCLOAK_REALM_IMPORT_DIR`. Build
it by merging only the exported user records into the reviewed production
template; development clients, URLs and realm settings are never promoted:

```bash
python scripts/merge-keycloak-production-users.py \
  --production-template pilot/keycloak/realm/production/jitsi-realm.json \
  --development-export /secure/backup/keycloak-export/jitsi-dev-realm.json \
  --output deploy/production/local/keycloak/realm-import/jitsi-realm.json
```

The source realm remains host-private. A network-isolated one-shot container
materializes it into a dedicated Docker volume readable by the rootless
Keycloak process; do not loosen the source directory or JSON permissions.

If the backend runtime-secret volume must be recreated after the initial Vault
bootstrap, issue a fresh five-minute, one-use handoff and start the backend
immediately:

```bash
sh scripts/reissue-production-backend-approle.sh
docker compose --env-file .env.production -f docker-compose.production.yml up -d backend frontend
```

When restoring the development application database for the first production
cutover, promote its single active config set before starting the backend. The
migration is transactional, refuses ambiguous state, replaces the meeting JWT
secret with an independently encrypted production value, and writes a durable
redacted audit event. Use `scripts/encrypt-config-set-secret.mjs` to reproduce
the application's AES-GCM payload format; never put plaintext secret values in
SQL files, command arguments, shell history, or logs.

The OIDC client secret is substituted from
`SSO_CLIENT_SECRET` in the Keycloak service-specific Vault delivery file.
Keycloak realm import supports environment placeholders; never replace the
placeholder with a committed secret.

`--import-realm` does not overwrite an existing realm. During this one-time
cutover, stop Keycloak and run
`scripts/migrate-keycloak-post-logout-policy.sql` against its private database;
the guarded migration explicitly allows only
`https://jitsi-mgorka.top/auth` as the post-logout redirect.

The backend's public redirect URI is:

```text
https://jitsi-mgorka.top/login/oauth2/code/keycloak
```

Nginx forwards `/login/oauth2/` and `/oauth2/` to the backend. Create production
users through the Keycloak administrative workflow, set their `tenantId`
attribute, and assign only the required realm role (`admin`,
`support-engineer`, or `participant`; elevated platform roles require a
separate approval).

The authoritative role and endpoint matrix is
[`docs/access-control.md`](access-control.md). Do not introduce a Keycloak role
without adding it to that matrix and to the backend allow-list with positive
and negative authorization tests.

`tenantId` is declared in both realm files as a managed, single-valued user
profile attribute. Only the administrative context can view or edit it; portal
users cannot change their own tenant. The stack validators reject a missing
declaration, relaxed permissions, missing requirement, or a value outside the
approved 1–64 character identifier format.

Production builds an optimized `jitsi-keycloak:26.7.0` image from the official
`quay.io/keycloak/keycloak:26.7.0` base and stores state in a dedicated
PostgreSQL service. Development remains independent. Treat every later
Keycloak change as a database migration: stop all
old nodes, back up the Keycloak data volume and realm/configuration artifacts,
review every intervening migration note, then start exactly one upgraded node
and wait for `/health/ready` before routing traffic. A schema upgraded by a
newer Keycloak image must not be rolled back by starting the old image against
the same volume; restore the matching pre-upgrade backup instead.

## Jitsi

The approved production conference release is `stable-11146-1`. Web, Prosody, Jicofo and
JVB are one compatibility group: never upgrade or roll back only a subset of
the four GHCR images. This release runs rootless and uses web ports 8000/8443;
the repository pins the reviewed manifest digest of every image and validators
reject mixed tags, changed digests and the retired volume layout.

After a recreation, verify that `/run` is owned by UID/GID 1000 with mode 1750
inside every Jitsi container. A tmpfs ownership change requires
`--force-recreate`; a plain restart retains the old container mount contract.

The backend and Jitsi must also share one token issuer. Development derives
`APP_MEETINGS_TOKEN_ISSUER`, `JWT_APP_ID` and `JWT_ACCEPTED_ISSUERS` from
`DEV_PORTAL_ORIGIN`; production derives all three assignments from
`APP_MEETINGS_TOKEN_ISSUER`. Do not add an independent Jitsi issuer override:
that makes correctly signed portal tokens fail at XMPP authentication.

`pilot/jitsi/web/custom-meet.production.conf` intentionally pins the canonical
portal return origin `https://jitsi-mgorka.top`. If the production origin ever
changes, update that file and `APP_FRONTEND_ORIGIN` together and rerun the
perimeter validator before recreating `jitsi-web`; Compose does not interpolate
the contents of file-backed configs.

Before changing the Jitsi release, archive and checksum the active web,
Prosody, Prosody custom-plugin, Jicofo and JVB configuration volumes. Preserve
the matching backup during rollback. After recreation, verify all of the
following before routing production traffic:

1. all four containers use the same image tag and are healthy;
2. root and both close-page paths redirect to the portal;
3. a room without a portal-issued JWT returns to the portal;
4. an issued JWT reaches the prejoin page and authenticates to the conference;
5. `/xmpp-websocket` completes an HTTP 101 upgrade with the `xmpp`
   subprotocol;
6. JVB listens on UDP 10000 and the host publishes only the approved UDP port;
7. leaving a joined conference returns the browser to the portal;
8. each running RepoDigest matches the four reviewed references in the Compose
   file.
8. two external clients complete real audio/video exchange.

## Vault internal-only secret zone

Vault is an internal-only secret zone attached only to `secret_net` and
`ops_net`; it has no host-published port. The committed
`deploy/vault/Dockerfile` uses an exact stable artifact path from the approved
mirror and verifies it against the matching checksum source.

Validate the secret plane:

```bash
npm run prod:secret:baseline:validate
npm run prod:secret:auth:validate
npm run prod:secret:delivery:validate
```

The canonical backend flow is a response-wrapped AppRole handoff to
`backend-vault-bootstrap`. Workload roles enforce `secret_id_num_uses=1`.
Database credentials use the Vault database secrets engine, with a documented
static-role transition for the current long-lived JVM runtime.

Operator access to Vault is allowed only through a private path, bastion или VPN.
Enable the file audit device using
`deploy/vault/bootstrap/enable-audit-file.sh.example`, then capture evidence
that `docker compose port vault 8200` returns no published port.

Vault unseal/recovery material is held by the recovery actor and must not be
placed in repository env files, Compose mounts, or ordinary operator notes.
Ownership and rotation governance are defined in
`deploy/vault/secret-governance-matrix.md`; emergency access and post-use
rotation are defined in `deploy/vault/break-glass-runbook.md`.

## Ubuntu 24 host control plane baseline

Apply the Ubuntu 24 host control plane baseline from `deploy/host/` only after
replacing the SSH allowlist placeholder and verifying an independent recovery
console. Validate it with:

```bash
npm run prod:host:baseline:validate
```

The baseline requires named administrators, key-only SSH, UFW default-deny
inbound policy, time synchronization, bounded journald retention, and an
explicit break-glass path. Do not reload SSH or enable UFW until the dry-run
and recovery checks in `deploy/host/README.md` pass.

## Start and migration

Prepare and provision the single-node Vault through the staged private operator
workflow. The bootstrap overlay is removed before the script returns; it does
not remain attached to recovery or seed material:

```bash
npm run prod:operator:prepare
npm run prod:vault:bootstrap
```

Move `deploy/production/local/vault/recovery/init.txt` and the complete
`deploy/production/local/custody/vault-ca/` directory to approved offline
custody before public cutover. The CA private key is needed only to issue or
renew Vault server certificates and must never be mounted into Vault. Two of
the three unseal shares are required after
a Vault restart; do not retain the recovery file on the application host.

Run the fail-closed deployment preflight and then start the stack:

```bash
npm run prod:preflight
npm run prod:up
```

`npm run prod:preflight:offline` skips only live DNS comparison for staging. It
still requires real operator files, coherent cross-service credentials, a
private production realm import and TLS files.

During a pre-cutover maintenance window, the application and identity services
may be started without nginx or the Jitsi media quartet. Verify that isolated
service plane before opening any host port:

```bash
sh scripts/smoke-production-private-plane.sh
```

The smoke check requires every private service to be healthy, validates the
canonical Keycloak issuer and the allowlisted OSV proxy, and fails if any
production container publishes a host port. It is not a substitute for the
external TLS, login, invite and two-client media checks below.

Flyway migrations run as part of backend startup. Do not run multiple backend
instances against an unverified migration state. Wait for health checks rather
than treating container `running` state as readiness.

To enable the private monitoring overlay:

```bash
npm run prod:baseline:up:monitoring
```

Grafana and Alertmanager remain on `ops_net`; access them through an operator
tunnel or a separately reviewed private reverse-proxy path.

## Production smoke checklist

1. `curl -I http://jitsi-mgorka.top` returns an HTTPS redirect.
2. `curl -fsS https://jitsi-mgorka.top/api/v1/health` returns healthy JSON.
3. The browser login flow returns through
   `/login/oauth2/code/keycloak` and `/api/v1/auth/me` contains the expected
   `tenantId` and role.
4. An administrator can create a room and meeting, and a participant can use a
   generated invite.
5. Two clients on different external networks can exchange audio and video.
6. `docker compose ps` reports healthy application services.
7. No host port other than TCP 80/443 and UDP 10000 is reachable externally.
8. Prometheus evaluates the committed rules and Alertmanager sends both firing
   and resolved notifications during the controlled drill.
9. `curl --http2 -fsS -o /dev/null -w '%{http_version}\n' https://jitsi-mgorka.top/healthz`
   prints `2` from an external client.

For rollback, preserve PostgreSQL, Keycloak, Vault and Jitsi configuration
volumes, stop the new application containers, restore the previously tested
image/tag set and matching configuration backup, and rerun the smoke
checklist. Never remove persistent volumes as an application rollback step.
