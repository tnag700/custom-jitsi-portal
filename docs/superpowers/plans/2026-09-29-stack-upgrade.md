# Staged stack upgrade implementation plan

> Execute inline using `superpowers:executing-plans`; the user approved the design and instructed implementation on 2026-09-29.

**Goal:** Upgrade all feasible application and server components, with verified compatibility and recoverable production cutovers.
**Architecture:** Preserve the existing service boundaries and configuration. Upgrade one compatibility group at a time and retain a tested previous release.
**Tech stack:** Java 25, Spring Boot, Qwik/Vite, Docker Compose, PostgreSQL, Redis, Keycloak, Jitsi, Vault and monitoring.
**Spec:** [Approved design](../specs/2026-09-29-stack-upgrade-design.md).

## Constraints and review focus

- Keep Spring BOM versions aligned; keep Qwik core/router/lint plugin aligned.
- Preserve release notifications for newer incompatible versions; do not label a held version as latest.
- Check SSR, auth, database migrations and production-only inventory classloading.
- Preserve data volumes, rollback images and operator-owned secrets; no perimeter changes.
- Stateful cutovers require backup/restore evidence. Vault requires an operator unseal quorum; Jitsi requires browser/media acceptance.
- Use the existing dedicated upgrade branch and checkout, which contains operator-managed deployment files. The user's instruction to proceed authorizes routine implementation choices and existing Git/deployment scope without another planning approval.

## Tasks

- [x] **1. Application dependencies.** Update `backend/build.gradle`, Gradle wrapper/checksum, PMD configuration if compatible, frontend manifests/locks and affected config/guards. Refresh the backend inventory defaults and `scripts/stack-version-baseline.json`. Verify `npm run verify` from a clean dependency installation; retain a component only for a demonstrated incompatibility.
- [x] **2. Release-channel regression.** Add a failing check that Qwik requests the current published npm channel, update the backend release adapter and audit baseline consistently, then run the focused Java/Node tests. Confirm the monitor still reports updates beyond compatible installed versions.
- [x] **3. Container candidates.** Resolve official tags/digests and update Compose, Dockerfiles, `.nvmrc`, engines, validators and current documentation. Build/test candidates on the server. Keep groups whose mandatory runtime acceptance cannot be completed at their current production version and document why.
- [x] **4. Recovery and stateful upgrades.** Inventory server images/disk/volumes, retain the current runtime images, back up both PostgreSQL databases and relevant state, and restore into isolated candidates. Apply documented PostgreSQL follow-up checks. Validate Keycloak login/migrations, Redis behavior, monitoring and gated Vault/Grafana transitions.
- [x] **5. Release verified groups.** Review the complete diff, run the appropriate repository and server gates, commit/push tested groups, merge to `main`, deploy only accepted groups and verify health, served release artifacts and version inventory. Remove temporary candidates without deleting persistent production data.
- [x] **6. Record outcome.** Write exact deployed versions, SHAs, checks and held candidates with reasons in `docs/stack-upgrade-2026-09-29.md`; include all remaining operator/manual gates.
