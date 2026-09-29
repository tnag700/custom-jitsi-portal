# Staged stack upgrade design

Date: 2026-09-29

## Goal and scope

Bring the portal's direct application dependencies, build tools, and all
repository-managed server images to the newest release that can be validated
with the existing application and production data. The admin **Versions and
CVE** inventory must report the versions actually deployed. Keep a component
at its current version when a newer release cannot pass its compatibility or
recovery gate; record the reason rather than presenting it as current.

This includes the development-only Swagger UI image and production monitoring
images. It does not include unrelated projects on the host, host OS upgrades,
router/firewall changes, or automatic installation from the admin page.

## Current evidence and target selection

The repository starts at commit `ed04a94c1d8de0beec1aec29336043fedf9011b2`.
The online version audit reports six reviewed-stack updates, but its Qwik
`beta` tag stops at beta.40 while npm's `latest` tag has beta.45. The backend
release adapter makes the same channel choice, so both checks must use the
reviewed Qwik channel consistently. The production host has about 3.2 GiB
free on a 30 GiB filesystem, and Vault 1.21.4 is initialized, unsealed, and
uses Shamir unseal keys.

| Compatibility group | Current | Candidate | Selection rule |
| --- | --- | --- | --- |
| Spring | Boot 4.1.0, Framework 7.0.8, Security 7.1.0, Modulith 2.1.0, springdoc 3.1.0 | Boot 4.1.1, Boot-managed Framework 7.0.9 and Security 7.1.1, Modulith 2.1.1, springdoc 3.1.1 | Keep the Boot BOM authoritative; verify generated API and persistent event handling. |
| Java build | Gradle 9.7.0, datasource-micrometer 2.2.1, ArchUnit 1.4.1, JaCoCo 0.8.13, PMD 6.55.0 | Gradle 9.8.0, datasource-micrometer 2.3.0, ArchUnit 1.5.1, JaCoCo 0.8.15, PMD 7.28.0 if its rule set and CPD task can be migrated | Keep Java 25; update wrapper checksum and guards. PMD 7 is conditional on equivalent quality checks. |
| Qwik frontend | Core, Router and ESLint plugin beta.38; Vite 7.3.6 | Matching beta.45 trio and Vite 8.3.1 | Qwik beta.40+ requires Vite 8; test SSR, browser hydration and routes together. |
| Frontend tools | TypeScript 5.9.3, ESLint 10.8.1, TypeScript-ESLint 8.67.0, Vitest 4.1.11 | TypeScript 6.0.3, ESLint 10.11.0, TypeScript-ESLint 8.71.0; retain Vitest 4.1.11 | TypeScript-ESLint 8.71 excludes TypeScript 7; Qwik beta.45 excludes Vitest 5. Refresh other direct patch/minor tools and lockfile within their peer ranges. |
| Conference | Jitsi stable-11146-1 (four images) | stable-11248 (four images) | Keep web, Prosody, Jicofo and JVB on one release with immutable digests. |
| Identity and data | Keycloak 26.7.0; PostgreSQL 18.4; Redis 8.4.5 | Keycloak 26.7.4; PostgreSQL 18.6; Redis 8.10.2 | Preserve volumes and test restore before each stateful cutover. Check PostgreSQL 18.6's documented follow-up actions. |
| Edge and build runtime | Nginx 1.30.4, Node 24.18.0, Alpine 3.22.5; Temurin 25 floating tags | Nginx stable 1.30.5, Node 24.21.0, Alpine 3.22.6; resolve reviewed Temurin 25 digests | Pin reviewed multi-architecture manifests and verify TLS, HTTP/3 and proxy behavior. |
| Monitoring and dev UI | Prometheus 3.13.2, Alertmanager 0.33.1, Grafana 11.6.14-security-04, Swagger UI 5.32.13 | Prometheus 3.15.0, Alertmanager 0.34.1, Grafana 13.2.2, Swagger UI 5.33.0 | Grafana's major migration needs a copy of its database and isolated dashboard check. |
| Secrets | Vault 1.21.4 | Vault 2.1.1 only after isolated recovery rehearsal and operator unseal readiness | Vault 1.21.4 is already the latest 1.21 patch. Never restart production Vault without an available unseal quorum. |

The latest advertised release is a candidate, not automatic evidence of
compatibility. Redis, Grafana, Vault, Jitsi and build-tool major/minor changes
remain conditional until the corresponding gate passes. Keep the current
version and publish the reason for any failed candidate.

## Upgrade sequence

1. Capture the current Git commit, image IDs, config checksums, volume sizes,
   health status, and a tested restore point. Resolve official tags and
   immutable image digests again immediately before each group. Establish
   enough disk headroom without removing active images, retained rollback
   images, or data volumes. Any cleanup uses an explicit allowlist.
2. Update the Spring and Java-build group, then the Qwik/Vite/frontend group.
   Regenerate npm locks and API artifacts. Align hard-coded version guards,
   backend inventory defaults and the offline/online release audit. Correct
   Qwik's monitored release channel so beta.45 is not hidden by the stale
   `beta` distribution tag.
3. Update stateless runtime images (Node, Alpine, Nginx, Swagger UI,
   Prometheus, Alertmanager) with pinned digests. Build and exercise each
   affected service before the next group.
4. Upgrade all four Jitsi images as one release. Verify config generation,
   JWT join, XMPP WebSocket, HTTP/3 and UDP 10000 media. If an authenticated
   browser/WebRTC check cannot be performed, do not claim conference
   acceptance or promote this group to production.
5. Upgrade PostgreSQL, Redis and Keycloak separately against preserved data.
   Verify backup restoration in isolation, then application login, database
   migrations, Redis-backed sessions and health after each cutover. Upgrade
   Grafana only after a copy of its dashboard database survives its major
   migration. Vault is last and requires an isolated snapshot/restore test
   plus an operator with enough Shamir shares to unseal it after restart.

Each group has its own reviewable commit and server candidate. Only a group
that passes local verification and the matching server smoke is promoted to
`main` and production. A failed group stops there; already healthy groups
stay deployed. Record deployed Git SHA, image digests and any held version.

## Verification and recovery

- Application gate: `npm run verify`, frontend tests/typecheck/client and SSR
  builds, backend tests/quality checks, OpenAPI contract regeneration and
  offline version consistency. Run the online release audit after updating
  its reviewed baseline.
- Server gate: production configuration validators, candidate image builds,
  service health, TLS/proxy routes, login/logout, admin Versions and CVE
  inventory, and representative portal and meeting flows. Verify direct and
  proxied paths separately.
- Stateful gate: produce pre-upgrade backups of both PostgreSQL databases,
  Vault state, Redis state where persistence matters, Grafana data and Jitsi
  configuration volumes; test restoration in an isolated environment before
  changing production data. Keep backups outside Git and avoid exposing
  credentials in logs or the assistant workspace.
- Rollback: stateless services use the previous source and image digest.
  Stateful services restore the matching pre-upgrade data snapshot before
  starting an older image; do not run old Keycloak/Vault/Grafana binaries
  against a schema already upgraded by a newer release. Keep the previous
  release and its digests until acceptance is complete.

Production deployment is gated by observed health, application-specific
smoke tests, backup/restore evidence, available disk space and, for Vault,
operator unseal readiness. The existing browser security restriction is not
a substitute for user-facing acceptance evidence.

## Primary release references

- [Spring Boot 4.1.1](https://spring.io/blog/2026/08/20/spring-boot-4-1-1-available-now/) and [Spring Modulith releases](https://spring.io/projects/spring-modulith/)
- [Qwik changelog](https://github.com/QwikDev/qwik/blob/main/packages/qwik/CHANGELOG.md), [Vite 8 migration](https://vite.dev/blog/announcing-vite8), [Vitest migration](https://vitest.dev/guide/migration/)
- [Gradle 9.8 release notes](https://docs.gradle.org/9.8.0/release-notes.html)
- [Jitsi releases](https://github.com/jitsi/docker-jitsi-meet/releases), [Keycloak releases](https://github.com/keycloak/keycloak/releases)
- [PostgreSQL 18.6 notes](https://www.postgresql.org/docs/release/18.6/), [Redis 8.10 notes](https://redis.io/docs/latest/develop/whats-new/8-10/)
- [Vault upgrade guide](https://developer.hashicorp.com/vault/docs/upgrade), [Nginx stable releases](https://nginx.org/en/download.html)
