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

### HTTP/3 edge degradation

1. Confirm that the public TCP `443` path is healthy and a QUIC-capable external
   client can still complete HTTP/2 fallback. Do not treat an HTTP/3 failure as
   a reason to restart JVB.
2. Check the dedicated HTTP/3 path end to end: public UDP `443` DNAT to VM UDP
   `443`, UFW `443/udp`, Docker UDP `443:8443`, and the Nginx QUIC listener.
   A working JVB UDP `10000` media path does not prove any of these checks.
3. Run `curl --http3-only` from a genuinely external client and verify the
   browser Network protocol column reports `h3`. Test LAN separately when it
   depends on split DNS or UDP `443` NAT hairpin.
4. Keep `ssl_early_data off`. Do not enable 0-RTT as an incident workaround for
   authentication, invite, or administrative requests.
5. If QUIC cannot be restored safely, serve `Alt-Svc: clear` over TCP on portal,
   auth and meet, keep the HTTP/2 fallback available, wait at least the largest
   advertised `ma` (`ma=300` during canary), and then remove only the UDP `443`
   listener/publication/NAT/firewall path. Leave JVB UDP `10000` unchanged.

### Refresh-token reuse

Treat `JitsiAuthRefreshReuseSpike` as a security event. Preserve bounded audit
evidence, revoke the affected refresh-token family, review Keycloak sessions,
and rotate credentials only through the documented Vault workflow. Do not log
or paste serialized tokens.

### Closure

Confirm the alert resolves, complete a user-path smoke check, link deployment
and incident evidence, and record any follow-up hardening. Use
`deploy/vault/break-glass-runbook.md` if emergency access was required.
