# Access control matrix

This document is the source of truth for portal authorization. The backend
enforces every rule below; frontend visibility is only a usability layer.

## Role domains

Platform roles come from the trusted Keycloak realm role claim or from the
`resource_access` entry for the configured portal client (`jitsi-backend`).
Roles belonging to any other OIDC client, arbitrary top-level `roles` claims,
and unknown role names are ignored.

| Platform role | Purpose |
| --- | --- |
| `admin` | Full portal administration, rooms, meetings, participants, invites, configuration changes and incident mutations |
| `system-admin` | Read-only operational and configuration diagnostics |
| `security-admin` | Read-only security and operational diagnostics |
| `support-engineer` | Read-only support diagnostics |
| `participant` | Authenticated portal user; does not grant administrative API access |
| guest (no platform role) | Anonymous access only through a valid guest invite |

Meeting roles are separate from platform roles:

| Meeting role | Jitsi capability |
| --- | --- |
| `host` | Host/moderator capability for one assigned user |
| `moderator` | Moderator capability |
| `participant` | Regular conference participant |

A platform administrator is not automatically a meeting host. The meeting
assignment controls the role embedded in the Jitsi JWT.

## Backend route matrix

| Route group | Anonymous | Authenticated user | Read-only operational roles | `admin` |
| --- | ---: | ---: | ---: | ---: |
| Health, login, CSRF bootstrap | Allow | Allow | Allow | Allow |
| Guest invite validate/exchange | Valid invite only | Valid invite only | Valid invite only | Valid invite only |
| Profile and user directory | Deny | Own tenant | Own tenant | Own tenant |
| Upcoming meetings | Deny | Assigned records only | Assigned records only | Assigned records only |
| Meeting access token | Deny | Assigned meeting role only | Assigned meeting role only | Assigned meeting role only |
| Admin dashboard, incidents and role history (`GET`) | Deny | Deny | Allow | Allow |
| Config sets (`GET`) | Deny | Deny | Allow | Allow |
| Framework version and CVE snapshot (`GET`) | Deny | Deny | Allow | Allow |
| Incident coordination/ticket mutations | Deny | Deny | Deny | Allow |
| Force framework CVE refresh | Deny | Deny | Deny | Allow |
| Config set mutations | Deny | Deny | Deny | Allow |
| Rooms, meetings, participant assignments and invite management | Deny | Deny | Deny | Allow |
| Any unmatched endpoint | Deny | Deny | Deny | Deny |

Tenant-scoped operations must resolve the tenant from the authenticated
principal and reject a different requested tenant. Resource-level meeting
access is resolved from participant assignments and defaults to deny when the
role is missing or unknown.

The Keycloak `tenantId` source attribute is managed and admin-only: an
end-user cannot view or edit it through the account profile. Realm imports and
administrative user creation must provide exactly one validated value; the
backend still treats the resulting claim as untrusted until OIDC validation
and tenant/resource checks complete.

## Guest invariants

- A guest receives only the meeting role `participant`.
- Invite tokens must be random, unexpired, not revoked and below their usage
  limit.
- Disabling guest access for a meeting blocks both new invites and exchange of
  existing invites.
- Guest invite exchange does not create a platform session or platform role.
- Direct access to the Jitsi landing page is not a guest authorization path.

## Change procedure

1. Add or change the Keycloak realm role.
2. Add the role to the backend allow-list only after its permissions are
   documented here.
3. Add positive and negative backend tests for every affected route group.
4. Update frontend navigation guards, but never use them as the only control.
5. Verify an authenticated user from another tenant and a user carrying an
   unrelated client role are denied.
