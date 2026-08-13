# Operations runbook

## Phase 1 alerting

Canonical alert definitions live in
`pilot/monitoring/prometheus/alert-rules.yml`. Start by recording the alert
name, environment, service, first firing time, and current deployment version.
Do not copy tokens, user identifiers, meeting identifiers, or request payloads
into incident notes.

### Backend or readiness alerts

1. Check `docker compose ps` and the backend health endpoint from inside
   `app_net`.
2. Inspect bounded logs using the alert window and the edge request ID.
3. For `JitsiBackendUnavailable`, verify PostgreSQL, Redis, and Keycloak health
   before restarting the backend.
4. For `JitsiConfigCompatibilityBroken` or readiness alerts, inspect the active
   config set and correct configuration drift before resuming joins.

### Join SLI alerts

1. Compare join attempt, success, failure, and latency series in the Jitsi
   Portal Grafana dashboard.
2. Check JVB health and UDP 10000 reachability from outside the LAN.
3. Confirm `JVB_ADVERTISE_IPS` contains exactly the VM LAN address and the
   router's public address for split-horizon ICE.
4. If signaling works but media fails, inspect both router layers for UDP NAT
   forwarding and symmetric-NAT behavior.

### Refresh-token reuse

Treat `JitsiAuthRefreshReuseSpike` as a security event. Preserve bounded audit
evidence, revoke the affected refresh-token family, review Keycloak sessions,
and rotate credentials only through the documented Vault workflow. Do not log
or paste serialized tokens.

### Closure

Confirm the alert resolves, complete a user-path smoke check, link deployment
and incident evidence, and record any follow-up hardening. Use
`deploy/vault/break-glass-runbook.md` if emergency access was required.
