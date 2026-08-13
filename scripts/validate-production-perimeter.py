from __future__ import annotations

import json

from _keycloak_guardrails import validate_portal_user_profile
from _python_guardrails import (
    assert_contains,
    assert_not_contains,
    assert_regex,
    assert_regex_count_at_least,
    fail,
    get_list_section_items,
    get_service_block,
    get_service_names,
    read_repo_text,
    repo_root,
)


def get_port_list(service_block: str) -> list[str]:
    return get_list_section_items(service_block, "ports")


def get_network_list(service_block: str) -> list[str]:
    return get_list_section_items(service_block, "networks")


def assert_no_published_ports(service_name: str, service_block: str) -> None:
    ports = get_port_list(service_block)
    if ports:
        fail(
            f"Service '{service_name}' must not publish host ports in production baseline. Found: {', '.join(ports)}"
        )


def assert_has_networks(service_name: str, service_block: str, required_networks: list[str]) -> None:
    networks = get_network_list(service_block)
    for network in required_networks:
        if network not in networks:
            fail(
                f"Service '{service_name}' must be attached to network '{network}'. Current networks: {', '.join(networks)}"
            )


def assert_no_networks(service_name: str, service_block: str, forbidden_networks: list[str]) -> None:
    networks = get_network_list(service_block)
    for network in forbidden_networks:
        if network in networks:
            fail(
                f"Service '{service_name}' must not be attached to network '{network}'. Current networks: {', '.join(networks)}"
            )


def assert_port_set(service_name: str, actual_ports: list[str], expected_ports: list[str]) -> None:
    if sorted(actual_ports) != sorted(expected_ports):
        fail(f"Service '{service_name}' must publish only {', '.join(expected_ports)}. Found: {', '.join(actual_ports)}")


def assert_network_internal(text: str, network_name: str, expected_internal: bool) -> None:
    assert_regex(text, rf"^  {network_name}:\s*$", f"Required network '{network_name}' is missing.")
    actual_internal = (
        __import__("re").search(
            rf"^  {network_name}:\s*\n(?:    .*\n)*?    internal:\s*true\s*$",
            text,
            flags=__import__("re").MULTILINE | __import__("re").DOTALL,
        )
        is not None
    )
    if actual_internal != expected_internal:
        fail(f"Network '{network_name}' must have internal={expected_internal}. Actual internal={actual_internal}.")


def main() -> None:
    root = repo_root()
    base_text = read_repo_text("docker-compose.production.yml", "Compose file")
    monitoring_text = read_repo_text("docker-compose.production.monitoring.yml", "Monitoring compose file")
    nginx_config_text = read_repo_text("deploy/nginx/portal.conf.example", "Nginx portal config")
    ip_bootstrap_config_text = read_repo_text("deploy/nginx/portal-ip.conf.example", "IP bootstrap config")
    jitsi_access_policy_text = read_repo_text(
        "pilot/jitsi/web/custom-meet.production.conf",
        "Jitsi production access policy",
    )
    application_prod_text = read_repo_text("backend/src/main/resources/application-prod.yml", "Spring production config")
    realm_path = root / "pilot/keycloak/realm/production/jitsi-realm.json"
    production_realm = json.loads(realm_path.read_text(encoding="utf-8"))
    validate_portal_user_profile(
        production_realm,
        label="Production Keycloak realm",
        require_seeded_tenants=False,
    )

    required_networks = {
        "edge_net": False,
        "app_net": True,
        "identity_net": True,
        "identity_data_net": True,
        "realtime_net": False,
        "data_net": True,
        "secret_net": True,
        "ops_net": True,
        "osv_egress_net": False,
    }
    for network_name, expected_internal in required_networks.items():
        assert_network_internal(base_text, network_name, expected_internal)

    assert_contains(
        base_text,
        "subnet: 172.28.240.0/24",
        "identity_net must pin a dedicated subnet for the trusted nginx reverse proxy address.",
    )

    nginx = get_service_block(base_text, "nginx")
    nginx_cert_bootstrap = get_service_block(base_text, "nginx-cert-bootstrap")
    frontend = get_service_block(base_text, "frontend")
    backend = get_service_block(base_text, "backend")
    osv_egress_proxy = get_service_block(base_text, "osv-egress-proxy")
    keycloak = get_service_block(base_text, "keycloak")
    keycloak_db = get_service_block(base_text, "keycloak-db")
    db = get_service_block(base_text, "db")
    redis = get_service_block(base_text, "redis")
    jitsi_web = get_service_block(base_text, "jitsi-web")
    jitsi_prosody = get_service_block(base_text, "jitsi-prosody")
    jitsi_jicofo = get_service_block(base_text, "jitsi-jicofo")
    jitsi_jvb = get_service_block(base_text, "jitsi-jvb")

    assert_contains(
        nginx,
        "image: nginx:1.30.4-alpine@sha256:97d490c12ba55b4946b01546d1c3ed324e8d41ab1c9fcb2a616aa470620e5b46",
        "Production edge must use the reviewed stable nginx patch release and manifest digest.",
    )
    assert_contains(
        nginx,
        "nc -z -w 2 127.0.0.1 8080 && nc -z -w 2 127.0.0.1 8443",
        "Production edge healthcheck must verify both unprivileged listeners.",
    )

    assert_contains(
        base_text,
        "file: ./pilot/jitsi/web/custom-meet.production.conf",
        "Production Jitsi access policy must use a file-backed Compose config compatible with a read-only rootfs.",
    )
    assert_contains(
        jitsi_access_policy_text,
        'set $portal_return_url "https://jitsi-mgorka.top";',
        "Production Jitsi access policy must return users to the portal origin.",
    )

    approved_jitsi_images = {
        "jitsi-web": (jitsi_web, "image: ghcr.io/jitsi/web:stable-11146-1@sha256:ff81559621732d3dfc4815f261d41fd826566833016ea772f4d43a77aa88fe9a"),
        "jitsi-prosody": (jitsi_prosody, "image: ghcr.io/jitsi/prosody:stable-11146-1@sha256:0e3d9ada40c03e6eef151348e0872dce7b4b1c16c173ff4a67afeae60aba2404"),
        "jitsi-jicofo": (jitsi_jicofo, "image: ghcr.io/jitsi/jicofo:stable-11146-1@sha256:a5da296923010dcc2daf6a02e6a183181906cb969a088ae90b97516bdeb9737f"),
        "jitsi-jvb": (jitsi_jvb, "image: ghcr.io/jitsi/jvb:stable-11146-1@sha256:6a7cec66c6a2fdd8ffd3a90101a0f8e3297aff29494f258caf1bcfbd418a17f3"),
    }

    approved_alpine_image = "image: alpine:3.22.5@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce"
    if base_text.count(approved_alpine_image) != 2:
        fail("Production one-shot services must use exactly two approved Alpine 3.22.5 image pins.")
    assert_contains(
        redis,
        "image: redis:8.4.5@sha256:efe6e2625e4601cd7119c4fb48b1c04cf3071f8b1729ede1216ceee8bc99742d",
        "Production Redis must pin the approved 8.4 security patch and manifest digest.",
    )
    for service_name, (service_block, image) in approved_jitsi_images.items():
        assert_contains(
            service_block,
            image,
            f"{service_name} must use the approved Jitsi stable-11146-1 release.",
        )
        assert_contains(
            service_block,
            "/run:size=16M,uid=1000,gid=1000,mode=1750,exec",
            f"{service_name} must give the rootless s6 user ownership of its writable /run tmpfs.",
        )
    assert_contains(
        jitsi_prosody,
        "http://127.0.0.1:5280/health",
        "Production Prosody must use its built-in HTTP health endpoint.",
    )
    if base_text.count("image: ghcr.io/jitsi/") != len(approved_jitsi_images):
        fail("Production baseline must define exactly the four approved Jitsi service images.")
    assert_not_contains(
        base_text,
        "stable-10741",
        "Production baseline must not retain the retired Jitsi stable-10741 tag.",
    )

    issuer_contract = "${APP_MEETINGS_TOKEN_ISSUER:?Set APP_MEETINGS_TOKEN_ISSUER}"
    issuer_assignments = [
        f"APP_MEETINGS_TOKEN_ISSUER={issuer_contract}",
        f"JWT_APP_ID={issuer_contract}",
        f"JWT_ACCEPTED_ISSUERS={issuer_contract}",
    ]
    expected_issuer_counts = [1, 2, 2]
    for assignment, expected_count in zip(issuer_assignments, expected_issuer_counts):
        if base_text.count(assignment) != expected_count:
            fail(
                "Production baseline must derive backend token issuance and both "
                f"Jitsi validators from one APP_MEETINGS_TOKEN_ISSUER. Expected "
                f"{expected_count} occurrence(s) of '{assignment}'."
            )

    published_ports = {service_name: get_port_list(get_service_block(base_text, service_name)) for service_name in get_service_names(base_text)}
    allowed_published_ports = {"80:8080", "443:8443", "10000:10000/udp"}
    actual_published_ports = [port for ports in published_ports.values() for port in ports]
    for port in actual_published_ports:
        if port not in allowed_published_ports:
            fail(f"Unexpected public port mapping found in production baseline: {port}")

    forbidden_patterns = ["8080", "8081", "5432", "6379", "9090", "9093", "3001", "9080", "8200"]
    for service_name, ports in published_ports.items():
        for port in ports:
            if port in allowed_published_ports:
                continue
            for forbidden in forbidden_patterns:
                if __import__("re").search(rf"(^|:){forbidden}(/|$|:)", port):
                    fail(f"Forbidden public port '{forbidden}' found on service '{service_name}': {port}")

    assert_port_set("nginx", get_port_list(nginx), ["80:8080", "443:8443"])
    assert_port_set("jitsi-jvb", get_port_list(jitsi_jvb), ["10000:10000/udp"])

    for service_name in ["nginx-cert-bootstrap", "frontend", "backend", "osv-egress-proxy", "keycloak", "keycloak-db", "db", "redis", "jitsi-web", "jitsi-prosody", "jitsi-jicofo"]:
        assert_no_published_ports(service_name, get_service_block(base_text, service_name))

    assert_has_networks("nginx", nginx, ["edge_net", "app_net", "identity_net", "realtime_net"])
    assert_contains(nginx_cert_bootstrap, "network_mode: none", "TLS certificate bootstrap must have no network access.")
    assert_contains(nginx, "ipv4_address: 172.28.240.10", "Nginx must pin its identity_net address so Keycloak can trust the strict edge proxy only.")
    assert_has_networks("frontend", frontend, ["app_net"])
    assert_has_networks("backend", backend, ["app_net", "identity_net", "data_net"])
    assert_has_networks("osv-egress-proxy", osv_egress_proxy, ["app_net", "osv_egress_net"])
    assert_has_networks("backend-vault-bootstrap", get_service_block(base_text, "backend-vault-bootstrap"), ["secret_net"])
    assert_has_networks("keycloak", keycloak, ["identity_net", "identity_data_net"])
    assert_has_networks("keycloak-db", keycloak_db, ["identity_data_net"])
    assert_has_networks("db", db, ["data_net"])
    assert_has_networks("redis", redis, ["data_net"])
    assert_has_networks("jitsi-web", jitsi_web, ["realtime_net"])
    assert_has_networks("jitsi-prosody", jitsi_prosody, ["realtime_net"])
    assert_has_networks("jitsi-jicofo", jitsi_jicofo, ["realtime_net"])
    assert_has_networks("jitsi-jvb", jitsi_jvb, ["realtime_net"])

    assert_no_networks("frontend", frontend, ["data_net", "secret_net", "edge_net"])
    assert_no_networks("backend", backend, ["edge_net", "secret_net", "ops_net"])
    assert_no_networks("osv-egress-proxy", osv_egress_proxy, ["edge_net", "identity_net", "identity_data_net", "data_net", "secret_net", "ops_net", "realtime_net"])
    assert_no_networks("nginx", nginx, ["data_net", "secret_net", "ops_net"])
    assert_no_networks("keycloak", keycloak, ["edge_net", "data_net", "secret_net", "ops_net"])
    assert_no_networks("keycloak-db", keycloak_db, ["edge_net", "identity_net", "data_net", "secret_net", "ops_net"])

    assert_contains(nginx, "${NGINX_PORTAL_CONFIG_PATH:-./deploy/nginx/portal.conf.example}", "Nginx must default to deploy/nginx/portal.conf.example for production baseline.")
    assert_not_contains(nginx, "./deploy/nginx/portal-ip.conf.example", "IP-only bootstrap config must not become the production baseline mount target.")
    assert_contains(keycloak, "KC_HOSTNAME_STRICT=true", "Keycloak must keep strict hostname mode enabled in production baseline.")
    assert_contains(keycloak, "image: jitsi-keycloak:26.7.0", "Production must use the locally optimized approved Keycloak patch.")
    assert_contains(keycloak, "KC_DB=postgres", "Production Keycloak must use PostgreSQL rather than the development file store.")
    assert_contains(keycloak, "/health/ready", "Production Keycloak healthcheck must verify the management readiness endpoint.")
    assert_contains(keycloak, "KC_PROXY_HEADERS=xforwarded", "Keycloak must use xforwarded proxy headers mode in production baseline.")
    assert_contains(keycloak, "KC_PROXY_TRUSTED_ADDRESSES=${KC_PROXY_TRUSTED_ADDRESSES:-172.28.240.10}", "Keycloak must trust only the pinned nginx reverse proxy address by default.")
    assert_contains(osv_egress_proxy, "OSV_TARGET_HOST=api.osv.dev", "The CVE egress proxy must allow only the OSV API hostname.")
    assert_contains(osv_egress_proxy, "OSV_TARGET_PORT=443", "The CVE egress proxy must allow only TLS to OSV.")
    assert_contains(backend, "-Dhttps.proxyHost=osv-egress-proxy", "Backend CVE checks must use the allowlisted egress proxy.")
    assert_not_contains(keycloak, "9000:", "Keycloak management port 9000 must not be published in production baseline.")
    for forbidden in ["10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.1,::1"]:
        assert_not_contains(keycloak, forbidden, f"Keycloak trusted proxy baseline must not use broad private-range defaults like '{forbidden}'.")

    assert_contains(application_prod_text, "forward-headers-strategy: framework", "Spring Boot production profile must keep framework-managed forwarded header strategy.")
    assert_contains(ip_bootstrap_config_text, "bootstrap-only fallback", "IP-only nginx template must stay explicitly documented as bootstrap-only.")
    assert_contains(ip_bootstrap_config_text, "not the production hardening source of truth", "IP-only nginx template must remain outside the production source of truth.")

    assert_not_contains(nginx_config_text, "$proxy_add_x_forwarded_for", "Production nginx config must not preserve client-supplied X-Forwarded-For chains.")
    assert_regex_count_at_least(nginx_config_text, r"proxy_set_header\s+X-Forwarded-For\s+\$remote_addr;", 3, "Production nginx config must overwrite X-Forwarded-For with trusted edge remote_addr in all vhosts.")
    assert_regex_count_at_least(nginx_config_text, r"proxy_set_header\s+X-Request-Id\s+\$request_id;", 3, "Production nginx config must forward edge-generated request IDs to downstream services.")
    assert_contains(nginx_config_text, "log_format edge_combined", "Production nginx config must define correlation-friendly edge access logging.")
    assert_contains(nginx_config_text, "access_log /var/log/nginx/access.log edge_combined;", "Production nginx config must enable correlation-friendly edge access logging.")

    invite_log_match = __import__("re").search(
        r"log_format\s+edge_invite_redacted\s+(.+?);",
        nginx_config_text,
        flags=__import__("re").DOTALL,
    )
    if invite_log_match is None:
        fail("Production nginx must define a redacted access-log format for bearer invite paths.")
    invite_log_format = invite_log_match.group(1)
    regex = __import__("re")
    for forbidden_variable in ["$request", "$request_uri", "$uri", "$args", "$http_referer"]:
        if regex.search(rf"{regex.escape(forbidden_variable)}(?![A-Za-z0-9_])", invite_log_format):
            fail(
                "Redacted invite access logs must not contain request targets, arguments or referrers "
                f"that can disclose the bearer token. Found '{forbidden_variable}'."
            )
    invite_locations = regex.findall(
        r"location\s+~\*\s+\^/invite\(\?:/\|\$\)\s*\{\s*(.*?)\n    \}",
        nginx_config_text,
        flags=regex.DOTALL,
    )
    if len(invite_locations) != 4:
        fail(
            "Production nginx must define four case-insensitive protected /invite/<token> locations: "
            "one before the HTTP redirect and one on each HTTPS hostname."
        )
    residual_encoding_locations = regex.findall(
        r"location\s+~\*\s+%\[0-9a-f\]\[0-9a-f\]\s*\{\s*(.*?)\n    \}",
        nginx_config_text,
        flags=regex.DOTALL,
    )
    if len(residual_encoding_locations) != 4:
        fail(
            "Every production hostname must reject residual percent encoding before generic routing "
            "so nested invite encodings cannot reach URI-bearing logs."
        )
    for residual_encoding_location in residual_encoding_locations:
        assert_contains(
            residual_encoding_location,
            "access_log /var/log/nginx/access.log edge_invite_redacted;",
            "Residual-encoding rejects must use the token-safe access-log format.",
        )
        assert_contains(
            residual_encoding_location,
            "error_log /dev/null crit;",
            "Residual-encoding rejects must suppress URI-bearing nginx error logs.",
        )
        assert_contains(
            residual_encoding_location,
            "return 400;",
            "Residual percent encoding must fail closed before an upstream or generic location.",
        )
    for invite_location in invite_locations:
        assert_contains(
            invite_location,
            "access_log /var/log/nginx/access.log edge_invite_redacted;",
            "Every invite location on every hostname must use the redacted bearer-token log format.",
        )
        assert_contains(
            invite_location,
            'add_header Cache-Control "no-store" always;',
            "Every invite response on every hostname must be no-store.",
        )
        assert_contains(
            invite_location,
            'add_header X-Robots-Tag "noindex, nofollow, noarchive" always;',
            "Every HTTP and HTTPS invite response must stay out of search indexes and archives.",
        )
        assert_contains(
            invite_location,
            "error_log /dev/null crit;",
            "Invite locations must suppress URI-bearing nginx error logs for bearer-token paths.",
        )
        assert_contains(
            invite_location,
            "limit_req zone=invite_sensitive burst=20 nodelay;",
            "Browser invite routes must rate-limit the SSR validation/exchange flow before internal API calls.",
        )
        assert_contains(
            invite_location,
            "limit_conn per_ip_conn 10;",
            "Browser invite routes must bound concurrent SSR validation/exchange requests per source address.",
        )
    if sum("return 302 https://${PORTAL_HOST}$request_uri;" in location for location in invite_locations) != 3:
        fail("HTTP, auth-host and meet-host invite paths must redirect to the canonical portal hostname.")
    if sum("proxy_pass http://frontend_upstream;" in location for location in invite_locations) != 1:
        fail("Exactly one redacted invite location must proxy the HTTPS invite page to the frontend.")
    retired_invite_locations = regex.findall(
        r"location\s+~\*\s+\^/api/v1/invites/\[\^/\]\+/validate/\?\$\s*\{\s*(.*?)\n    \}",
        nginx_config_text,
        flags=regex.DOTALL,
    )
    if len(retired_invite_locations) != 4:
        fail("Every production hostname must redact and retire the legacy token-bearing invite validation URL.")
    for retired_invite_location in retired_invite_locations:
        assert_contains(
            retired_invite_location,
            "access_log /var/log/nginx/access.log edge_invite_redacted;",
            "Retired token-bearing invite URLs must use redacted access logs.",
        )
        assert_contains(
            retired_invite_location,
            "error_log /dev/null crit;",
            "Retired token-bearing invite URLs must suppress URI-bearing nginx error logs.",
        )
        assert_contains(
            retired_invite_location,
            'add_header Cache-Control "no-store" always;',
            "Retired token-bearing invite URL responses must be no-store.",
        )
        assert_contains(
            retired_invite_location,
            "return 410;",
            "Retired token-bearing invite validation URLs must never reach an upstream service.",
        )
    assert_contains(
        nginx_config_text,
        "limit_req_zone $binary_remote_addr zone=invite_sensitive:10m rate=10r/s;",
        "Public invite validation and exchange must use a dedicated bounded rate-limit zone.",
    )
    invite_api_locations = regex.findall(
        r"location\s+=\s+/api/v1/invites/(?:validate|exchange)\s*\{\s*(.*?)\n    \}",
        nginx_config_text,
        flags=regex.DOTALL,
    )
    if len(invite_api_locations) != 2:
        fail("Production nginx must define protected exact locations for invite validation and exchange.")
    for invite_api_location in invite_api_locations:
        assert_contains(
            invite_api_location,
            "access_log /var/log/nginx/access.log edge_invite_redacted;",
            "Invite API access logs must omit caller-controlled referrers and request targets.",
        )
        assert_contains(
            invite_api_location,
            "limit_req zone=invite_sensitive burst=20 nodelay;",
            "Invite API endpoints must apply the dedicated bounded rate limit.",
        )
        assert_contains(
            invite_api_location,
            'add_header Cache-Control "no-store" always;',
            "Invite API responses must be no-store.",
        )
        assert_contains(
            invite_api_location,
            'proxy_set_header Referer "";',
            "Invite API endpoints must not forward bearer-bearing referrers downstream.",
        )
        assert_contains(
            invite_api_location,
            "proxy_pass http://backend_upstream;",
            "Invite API endpoints must proxy only to the backend.",
        )
    assert_regex(
        nginx_config_text,
        r"server_name\s+\$\{PORTAL_HOST\}\s+\$\{AUTH_HOST\}\s+\$\{MEET_HOST\};"
        r"[\s\S]*?location\s+~\*\s+\^/invite\(\?:/\|\$\)[\s\S]*?edge_invite_redacted;"
        r"[\s\S]*?return\s+302\s+https://\$\{PORTAL_HOST\}\$request_uri;"
        r"[\s\S]*?location\s+/\s*\{\s*return\s+301\s+https://\$host\$request_uri;\s*\}",
        "The HTTP redirect vhost must route through locations so invite requests can select redacted logging first.",
    )

    for required_template_value in [
        "server_name ${PORTAL_HOST} ${AUTH_HOST} ${MEET_HOST};",
        "server_name ${PORTAL_HOST};",
        "server_name ${AUTH_HOST};",
        "server_name ${MEET_HOST};",
        "ssl_certificate /etc/nginx/certs/fullchain.pem;",
        "ssl_certificate_key /etc/nginx/certs/privkey.pem;",
        "listen 8080;",
        "listen 8443 ssl;",
        "http2 on;",
    ]:
        assert_contains(
            nginx_config_text,
            required_template_value,
            f"Production nginx envsubst template is missing '{required_template_value}'.",
        )
    regex = __import__("re")
    if nginx_config_text.count("http2 on;") != 3:
        fail("Production nginx must enable HTTP/2 exactly once in each public TLS vhost.")
    if len(regex.findall(r"(?m)^\s*listen\s+8443\s+ssl;\s*$", nginx_config_text)) != 3:
        fail("Production nginx must define exactly three IPv4 TLS listeners on 8443.")
    if len(regex.findall(r"(?m)^\s*listen\s+\[::\]:8443\s+ssl;\s*$", nginx_config_text)) != 3:
        fail("Production nginx must define exactly three IPv6 TLS listeners on 8443.")
    if regex.search(r"(?m)^\s*listen\b[^;]*\bhttp2\b[^;]*;", nginx_config_text):
        fail("Production nginx must use the current server-level HTTP/2 directive.")
    for forbidden_example_host in ["portal.example.com", "auth.example.com", "meet.example.com"]:
        assert_not_contains(
            nginx_config_text,
            forbidden_example_host,
            f"Production nginx envsubst template must not retain placeholder host '{forbidden_example_host}'.",
        )

    assert_contains(
        nginx_config_text,
        "server jitsi-web:8000;",
        "Production nginx must use the rootless Jitsi web HTTP port 8000.",
    )

    stripped_proxy_headers = [
        "Forwarded",
        "X-Forwarded-Prefix",
        "X-Original-Forwarded-For",
        "X-Original-URL",
        "X-Original-Method",
        "X-Forwarded-Access-Token",
        "traceparent",
        "tracestate",
        "baggage",
        "b3",
        "x-b3-traceid",
        "x-b3-spanid",
        "x-b3-parentspanid",
        "x-b3-sampled",
        "x-b3-flags",
        "uber-trace-id",
        "x-ot-span-context",
    ]
    for header_name in stripped_proxy_headers:
        directive = f'proxy_set_header {header_name} "";'
        if nginx_config_text.count(directive) < 3:
            fail(
                f"Production nginx must strip untrusted '{header_name}' at every public TLS vhost."
            )

    for directive in [
        "limit_req_zone $binary_remote_addr zone=auth_sensitive:10m rate=5r/s;",
        "limit_req_zone $binary_remote_addr zone=api_sensitive:10m rate=20r/s;",
        "limit_conn_zone $binary_remote_addr zone=per_ip_conn:10m;",
        "limit_req zone=auth_sensitive burst=10 nodelay;",
        "limit_req zone=api_sensitive burst=20 nodelay;",
    ]:
        assert_contains(nginx_config_text, directive, f"Production nginx config is missing required directive '{directive}'.")
    assert_regex_count_at_least(nginx_config_text, r"limit_conn\s+per_ip_conn\s+(10|20);", 4, "Production nginx config must enforce per-IP connection limits in vhost scopes.")

    for required_directive in [
        "client_max_body_size 2m;",
        "client_body_timeout 15s;",
        "client_header_timeout 15s;",
        "proxy_connect_timeout 5s;",
        "proxy_read_timeout 30s;",
        "proxy_send_timeout 30s;",
    ]:
        assert_contains(nginx_config_text, required_directive, f"Production nginx config is missing required edge hardening directive '{required_directive}'.")

    for location in [
        "location ^~ /actuator/",
        "location = /actuator/health",
        "location ~* ^/(swagger-ui|swagger-ui\\.html|v3/api-docs|api-docs|prometheus|metrics|env|configprops)(/|$)",
        "location = /admin",
        "location ^~ /admin/",
        "location = /realms/master",
        "location ^~ /realms/master/",
        "location ~* ^/(metrics|health)(/|$)",
        "location ~* ^/realms/[^/]+/(metrics|health)(/|$)",
        "location /realms/",
        "location ^~ /resources/",
        "location ^~ /.well-known/",
        "location /xmpp-websocket",
        "location /colibri-ws",
    ]:
        assert_contains(nginx_config_text, location, f"Production nginx config is missing required protected location '{location}'.")

    if nginx_config_text.count("proxy_pass http://keycloak_upstream;") != 4:
        fail(
            "Production nginx must proxy only the four explicit Keycloak public surfaces; "
            "a root fallback or another public path is forbidden."
        )
    assert_regex_count_at_least(
        nginx_config_text,
        r"location\s+/\s*\{\s*return\s+404;\s*\}",
        1,
        "Production Keycloak vhost must deny unmatched public paths.",
    )
    assert_regex_count_at_least(
        nginx_config_text,
        r"location\s+/(xmpp-websocket|colibri-ws)\s*\{[^}]*proxy_read_timeout\s+900s;[^}]*proxy_send_timeout\s+900s;",
        2,
        "Production Jitsi websocket routes must keep explicit long-lived read and send timeouts.",
    )

    monitoring_backend = get_service_block(monitoring_text, "backend")
    prometheus = get_service_block(monitoring_text, "prometheus")
    alertmanager = get_service_block(monitoring_text, "alertmanager")
    mock_alert_receiver = get_service_block(monitoring_text, "mock-alert-receiver")
    grafana = get_service_block(monitoring_text, "grafana")

    for service_name in get_service_names(monitoring_text):
        assert_no_published_ports(service_name, get_service_block(monitoring_text, service_name))

    assert_has_networks("backend (monitoring overlay)", monitoring_backend, ["app_net", "identity_net", "data_net", "ops_net"])
    assert_no_networks("backend (monitoring overlay)", monitoring_backend, ["edge_net", "secret_net"])
    assert_has_networks("prometheus", prometheus, ["app_net", "ops_net"])
    assert_has_networks("alertmanager", alertmanager, ["ops_net"])
    assert_has_networks("mock-alert-receiver", mock_alert_receiver, ["ops_net"])
    assert_has_networks("grafana", grafana, ["ops_net"])

    print("validate-production-perimeter: OK")
    print("validate-production-perimeter: verified public exposure is limited to 80/tcp, 443/tcp and 10000/udp")
    print("validate-production-perimeter: verified trust-zone networks and private-service membership")
    print("validate-production-perimeter: verified frontend SSR has no direct data_net/secret_net access, backend stays off the secret plane, and only backend-vault-bootstrap joins secret_net for auth bootstrap")
    print("validate-production-perimeter: verified strict forwarded-header overwrite, throttling controls and blocked debug/management surfaces")
    print("validate-production-perimeter: verified Keycloak trusted proxy policy and framework-managed backend forwarded headers")


if __name__ == "__main__":
    main()
