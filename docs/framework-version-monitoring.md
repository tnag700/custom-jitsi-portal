# Framework version and CVE monitoring

The admin console exposes a backend-owned software inventory at
`/admin/framework-versions`. It monitors the runtime Spring versions and the
locked Qwik, Qwik Router, Qwik UI, Express, Vite, TypeScript, Tailwind,
Vitest and ESLint versions used by the frontend. Spring Retry and Spring
Modulith are included alongside the runtime Spring platform.

## Data flow

1. The backend builds a fixed inventory. API callers cannot submit package
   names, versions or provider URLs.
2. The OSV adapter queries `https://api.osv.dev/v1/query` with a five-second
   request timeout, disabled redirects and a one-megabyte response limit.
3. Provider data is normalized to bounded plain text, known identifiers,
   matching fixed versions and HTTPS advisory links.
4. The result is cached for six hours. A scheduled refresh runs every six
   hours; the first admin request also creates a snapshot when none exists.
5. A failed refresh preserves the previous result as `stale`. It never clears
   a previously known critical finding merely because the provider is down.

The admin monitor answers “is the installed version affected by a known
vulnerability?”. Security status and release freshness are intentionally
separate signals: zero OSV findings must never be presented as “latest”.

`npm run stack:versions:audit` compares reviewed compatibility-group versions
with fixed official Maven, Gradle and npm release channels. The command exits
with code `2` when an update is available. A weekly GitHub Actions workflow runs
the same online audit, while `npm run verify` runs the offline consistency check
so a dependency bump cannot silently drift away from the reviewed baseline.
Prerelease packages use an explicit channel (`beta` for Qwik); the audit never
mistakes npm's older `latest` tag for a downgrade.

## Severity policy

A global critical notification is raised only when the source explicitly
classifies an advisory as `CRITICAL` in its categorical database or ecosystem
metadata. Missing or vector-only severity remains `unknown`; the portal does
not invent a critical rating. All other known advisories remain visible on the
component card.

## Authorization

- `admin`, `system-admin`, `security-admin` and `support-engineer` can read the
  cached snapshot.
- Only `admin` can force an immediate refresh.
- The browser never contacts OSV directly and receives no provider token or
  raw response.

Backend authorization is authoritative. Hiding the refresh button is only a
frontend usability control.

## Configuration

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `APP_VERSION_MONITOR_ENABLED` | `true` | Enable advisory checks |
| `APP_VERSION_MONITOR_CACHE_TTL` | `PT6H` | Snapshot lifetime |
| `APP_VERSION_MONITOR_SCHEDULE_ENABLED` | `true` | Enable background refresh |
| `APP_VERSION_MONITOR_INITIAL_DELAY` | `PT1M` | Delay before the first scheduled scan |
| `APP_VERSION_MONITOR_FIXED_DELAY` | `PT6H` | Delay between scheduled scans |
| `APP_VERSION_MONITOR_QWIK_VERSION` | locked package version | Override Qwik inventory version |
| `APP_VERSION_MONITOR_QWIK_ROUTER_VERSION` | locked package version | Override Qwik Router inventory version |
| `APP_VERSION_MONITOR_EXPRESS_VERSION` | locked package version | Override Express inventory version |
| `APP_VERSION_MONITOR_QWIK_UI_VERSION` | locked package version | Override Qwik UI inventory version |
| `APP_VERSION_MONITOR_VITE_VERSION` | locked package version | Override Vite inventory version |
| `APP_VERSION_MONITOR_TYPESCRIPT_VERSION` | locked package version | Override TypeScript inventory version |
| `APP_VERSION_MONITOR_TAILWIND_VERSION` | locked package version | Override Tailwind inventory version |
| `APP_VERSION_MONITOR_VITEST_VERSION` | locked package version | Override Vitest inventory version |
| `APP_VERSION_MONITOR_ESLINT_VERSION` | locked package version | Override ESLint inventory version |

`scripts/validate-dev-stack-config.py` verifies that all nine default frontend
versions match `frontend-qwik/package-lock.json`, preventing silent inventory
drift after an upgrade.

The release audit does not auto-upgrade compatibility groups. Major updates
such as TypeScript/Vitest, Vault, Redis or Grafana still require a dedicated
migration and runtime acceptance pass.

## Operator response

1. Open the affected component and inspect the advisory and listed fixed
   versions.
2. Confirm applicability against the linked upstream advisory.
3. Upgrade the dependency on a dedicated branch and regenerate lockfiles.
4. Run `npm run verify`, rebuild the dev stack and repeat the relevant browser
   and integration smoke.
5. Deploy only after the critical count is cleared or an explicitly reviewed
   exception is documented.
