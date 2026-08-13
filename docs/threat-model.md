# Threat model

Snapshot date: 2026-07-30.

This document defines the trust boundaries and security invariants for the
portal, Keycloak, Jitsi, guest invites and the production perimeter. Review it
whenever authentication, authorization, public routes, secret delivery or
network topology changes.

## Assets and trust boundaries

| Asset | Trusted authority | Boundary |
| --- | --- | --- |
| Platform identity and role | Keycloak realm plus the configured portal client | Browser and bearer claims are untrusted until OIDC validation and role allow-list mapping complete |
| Tenant scope | Validated `tenantId` or `tenant_id` principal claim sourced from an admin-only managed Keycloak attribute | Request parameters, self-service profile data and resource identifiers never select an unrestricted tenant |
| Meeting role | Persisted participant assignment | A platform role never implies host or moderator privileges |
| Guest access | Random invite token and atomic usage reservation | The public exchange endpoint grants one meeting-scoped participant token only |
| Jitsi access | Portal-issued short-lived JWT | The Jitsi landing page and empty-JWT joins are not authorization paths |
| Application secrets | Vault policies and service-specific rendered files | Git, Compose environment examples, frontend bundles and logs are outside the secret boundary |
| Vulnerability intelligence | Backend-owned framework inventory and fixed OSV API endpoint | Browsers and API callers cannot select an outbound URL, package or version |
| Production data | PostgreSQL and Redis private networks | Only explicitly connected application services may reach data services; refresh-token replay/revocation state is durable in PostgreSQL, production forbids volatile fallback, and a persisted monotonic cutover epoch invalidates untracked legacy sessions |

## Principal threats and controls

| Threat | Required control | Regression evidence |
| --- | --- | --- |
| Role injection from an unrelated OIDC client | Accept known roles only from the realm and configured `resource_access` client; ignore arbitrary `ROLE_*` authorities | `OidcRoleAuthoritiesMapperTest` |
| Privilege escalation with an unknown role name | Closed `PortalRole` allow-list and deny-by-default route matcher | `OidcRoleAuthoritiesMapperTest`, `SecurityConfigRouteMatrixTest` |
| Cross-tenant IDOR | Resolve tenant from the authenticated principal and compare before querying or mutating | `TenantAccessGuardTest`, route/controller integration tests |
| Self-service tenant escalation | Declare `tenantId` as a managed, single-valued, admin-only Keycloak user-profile attribute and validate realm configuration | dev/production stack validators and OIDC browser smoke |
| Access to another meeting by identifier | Resolve a persisted participant assignment before issuing a Jitsi JWT | meeting role/token tests |
| Admin role becoming meeting moderator implicitly | Keep platform and meeting role domains separate | meeting role resolver tests and `access-control.md` |
| Invite guessing | Cryptographically secure high-entropy token generation; never log raw tokens | secure invite token generator tests |
| Invite replay or concurrent final use | Expiry, revoke and usage-limit validation plus atomic reservation/consume | invite validation concurrency and port contract tests |
| Guest privilege escalation | Guest exchange hard-codes meeting role `participant` and creates no portal session | invite exchange and meeting token tests |
| CSRF on authenticated mutations | Cookie CSRF token repository and required token on non-public mutations | security route matrix |
| Cross-origin credential abuse | Exact frontend origin, credentialed CORS and bounded allowed headers/methods | security route matrix and production perimeter validator |
| Direct service exposure | Public production ingress limited to proxy TCP 80/443 and JVB UDP 10000 | production perimeter validator |
| Secret disclosure | Vault-only runtime delivery, scoped policies, no secret plane access from frontend | production secret-plane validators |
| Jitsi service-page bypass | Root/close redirects to the portal, JWT required, empty JWT and guests disabled | Compose validators and dev HTTP smoke |
| SSRF or advisory-content injection through CVE monitoring | Fixed HTTPS OSV endpoint, predefined package inventory, bounded timeouts/body size, normalized plain-text fields and HTTPS advisory links only | version monitor parser/service tests and security route matrix |

## Security invariants

1. Backend authorization is authoritative; frontend navigation is not a
   security control.
2. Unknown routes, roles, tenants, meeting roles and invite states fail closed.
3. A role from one trust domain is never reused implicitly in another domain.
4. A request is authorized for both endpoint capability and target resource.
5. Guest exchange returns the minimum privilege and never creates a platform
   identity.
6. Secrets are injected at runtime and are not accepted through public API
   payloads.
7. Security failures use stable problem codes without exposing tokens, secrets
   or sensitive subject data.
8. Vulnerability-provider responses are untrusted data and never become raw
   HTML, executable links or caller-controlled outbound requests.

## Residual risks and required release evidence

- Container-backed PostgreSQL/Redis contention tests must run before release;
  in-memory concurrency tests alone are not sufficient evidence.
- Keycloak and Jitsi upgrades require a new login/logout/refresh/role/JWT/media
  smoke because their trust contracts can change between image versions.
- CSP and cookie attributes must be checked at the public HTTPS proxy; backend
  unit tests cannot prove the final edge response.
- Rate-limit exhaustion for login, refresh and invite exchange requires a
  production-like proxy/runtime test.
- Browser permission prompts and real UDP media need a manual or automated
  browser/media smoke on the deployment network.

## Change checklist

1. Identify the affected asset and trust boundary.
2. Add a negative test that demonstrates the attempted bypass.
3. Implement the smallest backend control and keep deny-by-default behavior.
4. Run backend, frontend, contract and configuration gates.
5. Verify the affected flow and its denial case on the dev stack.
6. Update this model and `access-control.md` if the trust contract changed.
