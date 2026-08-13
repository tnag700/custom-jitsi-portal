# Review and refactoring roadmap

Snapshot date: 2026-07-30.

This roadmap records the evidence-driven path from the current development
baseline to production readiness. It is intentionally incremental: preserve
working behavior, tighten trust boundaries first, then simplify modules and
upgrade one compatibility group at a time.

## Baseline

### Stack

| Area | Current repository baseline | Upgrade direction |
| --- | --- | --- |
| Backend | Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Gradle 9.7 | Keep the compatibility group aligned and review future feature-line changes separately |
| Frontend | Qwik 2.0.0-beta.38, Vite 7.3.6, TypeScript 5.9 | Qwik beta/UI group aligned; move Vite/Vitest together only after Qwik declares Vitest 4 compatibility |
| Node runtime | Node 24.18.0 LTS in `.nvmrc`, package engines and containers | Keep the supported LTS line aligned across local, build and runtime environments |
| Data | PostgreSQL 18.4, Redis 8.4 | Retain supported major lines and pin approved patches/digests |
| Identity | Keycloak 26.7.0 | Keep the approved patch aligned across development and production after migration smoke tests |
| Conference | Jitsi `stable-11146-1` | Keep all four conference images aligned and repeat JWT/WebSocket/UDP smoke tests before each upgrade |

Version evidence must be refreshed immediately before an upgrade PR. The
current upstream snapshot showed Spring Boot 4.1.0, PostgreSQL 18.4,
Keycloak 26.7.0 and Jitsi stable-11146-1.

### Quality gates

- Backend: 775 tests, 0 failures, 0 errors, 39 skipped in the last full report.
- Frontend: 477 tests across 72 files, all passing.
- Frontend typecheck, client build, SSR build, ESLint and architecture lint pass.
- Blocking PMD, ArchUnit/Spring Modulith checks and CPD pass.
- The non-blocking PMD audit reports 2,061 findings; 1,658 are
  `LawOfDemeter`. This profile is too noisy to use as a refactoring backlog and
  must be tuned rather than mechanically "fixed".
- The committed OpenAPI snapshot and generated frontend API type initially
  drifted from the runtime contract.
- Local Docker Engine was unavailable during baseline collection; container
  and migration evidence must come from the dev host.

### Size and coupling indicators

- Backend production code contains 495 Java files; the meetings domain alone
  contains 151.
- Backend plus frontend production source contains 643 Java/TypeScript files.
- Sixteen production files exceed 300 lines. The largest non-generated
  hotspots are admin incident mapping/services, join preflight, the dashboard
  route, meeting route handlers, the meeting form and participant directory.
- Architecture boundaries already have useful ArchUnit and frontend import
  guards. The target is to improve these boundaries, not replace the system
  with a new framework.

## Completed in the first security slice

- Restricted OIDC authorities to the explicit portal role allow-list.
- Accepted client roles only from the configured `jitsi-backend` client.
- Ignored unrelated client roles, arbitrary top-level `roles` claims and
  pre-mapped `ROLE_*` authorities.
- Changed blank unknown-meeting-role configuration to deny access.
- Added positive and negative regression tests for role mapping.
- Added the canonical access-control matrix.
- Disabled Open Session in View and unused Spring Data Redis repository
  scanning.
- Removed unused root npm dependencies and the unused frontend example domain.
- Regenerated the OpenAPI snapshot and frontend API types.
- Upgraded the compatible backend feature group to Spring Boot 4.1.0 and
  Spring Modulith 2.1.0 with Gradle 9.7.
- Upgraded Datasource Micrometer to 2.2.1 and kept JDBC traces/Hikari pool
  metrics while filtering generic JDBC metric families that violate
  Prometheus' stable label-set requirement.
- Pinned development, production and container-backed tests to PostgreSQL
  18.4; the development volume was backed up and validated across the minor
  update.
- Aligned the frontend build, SSR runtime and monitoring helper on the
  supported Node 24 LTS line and added a repository-local version contract.
- Upgraded the isolated identity-service image from Keycloak 26.1.2 to 26.7.0
  after reviewing the cumulative 26.x migration notes. A clean import exposed
  that undeclared `tenantId` attributes were discarded, so both realms now
  declare an admin-only managed tenant attribute with validation and automated
  configuration guards.
- Verified the 26.7.0 dev realm import, OIDC discovery, admin and participant
  login, logout/session invalidation, role claims, tenant claims and backend
  denial of the participant on an admin API. Logout now also clears the
  authenticated client shell immediately instead of showing stale admin
  navigation.
- Upgraded all four Jitsi services from `stable-10741` to `stable-10978` as one
  compatibility group and added repository guards that reject a mixed or
  partially downgraded conference stack. The live join smoke exposed an
  issuer drift between the backend and Jitsi fallbacks; both dev and
  production compose baselines now derive token issuance and validation from
  one issuer variable, with regression guards.
- Aligned the development quartet with production on digest-pinned
  `stable-11146-1` and its rootless storage and listener contract.
- Verified the upgraded development stack with healthy aligned containers,
  portal redirects for root/close pages, a real HTTP 101 XMPP WebSocket
  handshake, UDP 10000 listener/publication, portal-issued JWT
  authentication, a joined single-participant conference and browser return
  to the personal cabinet after leaving. A checksummed pre-upgrade archive of
  all six active Jitsi volumes was retained on the development host.
- Replaced the 396-line meetings route-handler module with focused loaders and
  meeting, participant and invite actions while keeping the route contract
  stable.
- Reduced the 424-line cabinet route to a small route boundary with separate
  loaders, token action and page orchestration. Join redirects are now
  validated by a pure, directly tested HTTPS/origin/credentials policy before
  browser navigation.
- Split the profile route into loader, mutation action and presentation page.
  The redesigned page uses the shared panel/field/action hierarchy and states
  explicitly that profile editing cannot change platform roles.
- Reduced the 360-line role-history route to a thin boundary with a dedicated
  loader, bounded primary search, collapsed advanced filters and an event
  timeline that keeps technical identifiers behind disclosure. Russian role
  labels and UTC formatting have direct runtime and presentation tests.
- Removed the duplicate single-room selector and quick link, localized meeting
  type labels, documented each meeting format in an accessible radio-card
  control and allowed a single meeting card to use the available width.
- Added the canonical `npm run verify` repository gate. It generates OpenAPI
  once in the same Gradle invocation as backend tests and blocking quality
  checks, derives frontend API types from that snapshot, then runs the complete
  frontend build/test/architecture and static dev/production configuration
  gates.
- Removed an orphan traceability validator whose required BMAD audit,
  requirements matrix and pull-request template were never committed. Release
  requirements remain reviewable in the access-control matrix, threat model,
  this roadmap and executable regression/configuration guards.
- Upgraded Qwik core, router and ESLint plugin together to `2.0.0-beta.38`,
  Qwik UI to `0.7.7`, ESLint to 10.8 and the compatible Tailwind patch group.
  The official legacy-package aliases keep Qwik UI on the single Qwik 2
  runtime instead of installing a second Qwik 1 copy. Removed unused coverage
  and CSS-module tooling, locked the reviewed vulnerable transitives and
  reduced both the complete frontend and production npm audits from 21
  findings to zero without `npm audit fix --force`. The npm lifecycle policy
  permits only the pinned `esbuild@0.28.1` install script, explicitly denies
  the macOS-only `fsevents` script in Linux builds and fails closed for newly
  introduced install scripts.

## Prioritized backlog

### P0 — release blockers

1. Keep OpenAPI and generated frontend types as one verified contract change.
2. Run the complete backend/frontend gates after every security or dependency
   slice.
3. Build and start the updated stack on the dev host, apply all Flyway
   migrations to PostgreSQL and execute browser smoke tests.
4. Verify the external Jitsi landing page remains unavailable to ordinary
   users and conference exit returns to the portal.

### P1 — security and authorization

1. Keep the completed route/resource matrix aligned with
   `docs/access-control.md`; every new endpoint needs positive role coverage
   plus cross-tenant and unrelated-client-role denial where applicable.
2. Decide whether meeting creation belongs only to `admin` or requires a
   separate `organizer` platform role. Do not overload the meeting `host` role
   for platform administration.
3. Make rate limits for login, refresh and guest invite exchange observable
   and cover limit exhaustion in perimeter/runtime smoke tests.
4. Add automated production checks for cookie flags, CSP, CORS, disabled API
   docs and non-public management endpoints.
5. Keep guest exchange atomic and test concurrent final-use attempts against
   PostgreSQL/Redis on the container-backed suite.

### P1 — supported stack and dependency risk

1. Repeat the completed Keycloak 26.7.0 dev migration smoke against a restored
   production backup in staging before the separately authorized deployment.
2. Repeat the Jitsi `stable-11146-1` rootless migration smoke against staging
   with two external media-capable clients before production deployment.
3. Keep the completed Qwik/UI/toolchain group locked by the executable version
   guard and zero-advisory audit. Move Vite 7/Vitest 3 to their next majors
   only as a separate compatibility migration after Qwik declares Vitest 4
   support; do not use `npm audit fix --force`.
4. Treat Spring Boot 4.1 and Spring Modulith 2.1 as a separate compatibility
   migration after the 4.1.0/2.1.0 compatibility baseline is stable.

### P2 — architecture and maintainability

1. Simplify the meetings module by feature slice:
   - lifecycle and scheduling;
   - participant assignment;
   - invite management;
   - join/token issuance.
   Keep public ports stable while removing one-use wrappers and policy classes
   that do not encode an independent rule.
2. Continue the route/application-service split beyond the completed meetings,
   cabinet, profile and role-history slices. Routes should parse URL/session
   state and delegate; they should not own domain transitions.
3. Split admin incident read-model composition from presentation formatting.
   Avoid expanding the current mapper/policy graph.
4. Replace the noisy PMD audit profile with a small actionable warning budget:
   complexity, generic exception handling, unsafe locale conversion, excessive
   parameter lists and unused code.
5. Keep the completed top-level `npm run verify` gate mandatory for every
   dependency, security, contract or architecture slice.

### P2 — UX/UI

Browser review of the baseline dev build found:

- semantic landmarks and accessible button names are generally present;
- the admin console has a coherent navigation shell and useful operational
  hierarchy;
- user pages and admin pages look like different design systems;
- Russian UI still contains internal English values outside the completed
  meeting-format slice (`DEV`, `Portal`, `Meeting / Join Surface`);
- mobile meeting management becomes vertically long before the user reaches
  the meeting actions;
- typography, badges, button hierarchy and compact data presentation are not
  consistent across cabinet, meetings and admin.

The first UX slices are complete: a single room has one explicit navigation
action, meeting types use Russian presentation labels without changing stored
API values, the create/edit form explains all three formats, and a lone
meeting card fills the schedule width. Profile editing and administrative role
history now follow the same header/panel/field/action hierarchy, while the
cabinet diagnostics remain secondary to the primary join action. Meetings,
cabinet, profile and role history were checked live at desktop width and
390 px without horizontal overflow. Role history renders one page-level
heading, keeps advanced and technical context collapsed, and localizes the
development environment label.

Implementation sequence:

1. Continue normalizing terminology and display labels outside the completed
   meeting-format slice without changing stored/API enum values.
2. Define one page header, panel, status badge, field and action hierarchy in
   the existing design tokens.
3. Continue compacting participant/invite management below the completed room
   selection and schedule-card slice.
4. Apply the same shell to rooms, profile and cabinet.
5. Validate keyboard focus, dialog focus return, 390 px mobile layout and
   desktop layouts at 1280/1440 px after every visual slice.

## Target architecture

```text
Qwik route
  -> route loader/action (HTTP/session and URL only)
    -> frontend domain service
      -> generated OpenAPI client

Spring MVC adapter
  -> authenticated actor + tenant/resource guard
    -> application use case
      -> domain model/policy
        -> repository/integration port
          -> JPA, Redis, Keycloak or Jitsi adapter
```

Rules:

- Authentication produces a bounded `PortalRole`; authorization never consumes
  arbitrary token strings directly.
- Tenant and resource authorization happen before mutation.
- Meeting roles never grant portal administration.
- API adapters do not return persistence entities.
- Domain/application code does not import web, JPA or Redis types.
- Frontend route modules do not duplicate backend authorization rules.
- Generated API types are never edited manually.

## Verification per slice

1. Focused regression tests for the changed behavior.
2. Full backend test plus `validateQuality`.
3. Frontend test, typecheck, client/SSR build, lint and architecture lint.
4. OpenAPI drift and generated frontend type checks.
5. Dev Compose build/start and health checks.
6. Browser smoke for login, cabinet, rooms, meetings, participants, invites,
   profile, admin and conference exit.
7. Negative role/tenant/guest/Jitsi-surface checks.

Production deployment, DNS, router and firewall changes remain outside this
roadmap until separately authorized.
