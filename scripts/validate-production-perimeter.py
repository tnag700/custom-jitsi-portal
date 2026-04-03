from __future__ import annotations

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
    application_prod_text = read_repo_text("backend/src/main/resources/application-prod.yml", "Spring production config")

    required_networks = {
        "edge_net": False,
        "app_net": True,
        "identity_net": True,
        "realtime_net": False,
        "data_net": True,
        "secret_net": True,
        "ops_net": True,
    }
    for network_name, expected_internal in required_networks.items():
        assert_network_internal(base_text, network_name, expected_internal)

    assert_contains(
        base_text,
        "subnet: 172.28.240.0/24",
        "identity_net must pin a dedicated subnet for the trusted nginx reverse proxy address.",
    )

    nginx = get_service_block(base_text, "nginx")
    frontend = get_service_block(base_text, "frontend")
    backend = get_service_block(base_text, "backend")
    keycloak = get_service_block(base_text, "keycloak")
    db = get_service_block(base_text, "db")
    redis = get_service_block(base_text, "redis")
    jitsi_web = get_service_block(base_text, "jitsi-web")
    jitsi_prosody = get_service_block(base_text, "jitsi-prosody")
    jitsi_jicofo = get_service_block(base_text, "jitsi-jicofo")
    jitsi_jvb = get_service_block(base_text, "jitsi-jvb")

    published_ports = {service_name: get_port_list(get_service_block(base_text, service_name)) for service_name in get_service_names(base_text)}
    allowed_published_ports = {"80:80", "443:443", "10000:10000/udp"}
    actual_published_ports = [port for ports in published_ports.values() for port in ports]
    for port in actual_published_ports:
        if port not in allowed_published_ports:
            fail(f"Unexpected public port mapping found in production baseline: {port}")

    forbidden_patterns = ["8080", "8081", "5432", "6379", "9090", "9093", "3001", "9080", "8200"]
    for service_name, ports in published_ports.items():
        for port in ports:
            for forbidden in forbidden_patterns:
                if __import__("re").search(rf"(^|:){forbidden}(/|$|:)", port):
                    fail(f"Forbidden public port '{forbidden}' found on service '{service_name}': {port}")

    assert_port_set("nginx", get_port_list(nginx), ["80:80", "443:443"])
    assert_port_set("jitsi-jvb", get_port_list(jitsi_jvb), ["10000:10000/udp"])

    for service_name in ["frontend", "backend", "keycloak", "db", "redis", "jitsi-web", "jitsi-prosody", "jitsi-jicofo"]:
        assert_no_published_ports(service_name, get_service_block(base_text, service_name))

    assert_has_networks("nginx", nginx, ["edge_net", "app_net", "identity_net", "realtime_net"])
    assert_contains(nginx, "ipv4_address: 172.28.240.10", "Nginx must pin its identity_net address so Keycloak can trust the strict edge proxy only.")
    assert_has_networks("frontend", frontend, ["app_net"])
    assert_has_networks("backend", backend, ["app_net", "identity_net", "data_net"])
    assert_has_networks("backend-vault-bootstrap", get_service_block(base_text, "backend-vault-bootstrap"), ["secret_net"])
    assert_has_networks("keycloak", keycloak, ["identity_net"])
    assert_has_networks("db", db, ["data_net"])
    assert_has_networks("redis", redis, ["data_net"])
    assert_has_networks("jitsi-web", jitsi_web, ["realtime_net"])
    assert_has_networks("jitsi-prosody", jitsi_prosody, ["realtime_net"])
    assert_has_networks("jitsi-jicofo", jitsi_jicofo, ["realtime_net"])
    assert_has_networks("jitsi-jvb", jitsi_jvb, ["realtime_net"])

    assert_no_networks("frontend", frontend, ["data_net", "secret_net", "edge_net"])
    assert_no_networks("backend", backend, ["edge_net", "secret_net", "ops_net"])
    assert_no_networks("nginx", nginx, ["data_net", "secret_net", "ops_net"])
    assert_no_networks("keycloak", keycloak, ["edge_net", "data_net", "secret_net", "ops_net"])

    assert_contains(nginx, "${NGINX_PORTAL_CONFIG_PATH:-./deploy/nginx/portal.conf.example}", "Nginx must default to deploy/nginx/portal.conf.example for production baseline.")
    assert_not_contains(nginx, "./deploy/nginx/portal-ip.conf.example", "IP-only bootstrap config must not become the production baseline mount target.")
    assert_contains(keycloak, "KC_HOSTNAME_STRICT=true", "Keycloak must keep strict hostname mode enabled in production baseline.")
    assert_contains(keycloak, "KC_PROXY_HEADERS=xforwarded", "Keycloak must use xforwarded proxy headers mode in production baseline.")
    assert_contains(keycloak, "KC_PROXY_TRUSTED_ADDRESSES=${KC_PROXY_TRUSTED_ADDRESSES:-172.28.240.10}", "Keycloak must trust only the pinned nginx reverse proxy address by default.")
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
        "location ^~ /admin/",
        "location ~* ^/(metrics|health)(/|$)",
        "location ~* ^/realms/[^/]+/(metrics|health)(/|$)",
    ]:
        assert_contains(nginx_config_text, location, f"Production nginx config is missing required protected location '{location}'.")

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
