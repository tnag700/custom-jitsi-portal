from __future__ import annotations

import json

from _python_guardrails import (
    assert_contains,
    assert_list_contains,
    assert_list_startswith,
    assert_not_contains,
    assert_regex_not_match,
    command_exists,
    fail,
    get_list_section_items,
    get_scalar_value,
    get_service_block,
    read_repo_text,
    repo_root,
    run_command,
)


def get_volume_list(block: str) -> list[str]:
    return get_list_section_items(block, "volumes")


def assert_service_has_least_privilege(service_name: str, service_block: str) -> None:
    cap_drop = get_list_section_items(service_block, "cap_drop")
    security_opt = get_list_section_items(service_block, "security_opt")
    assert_list_contains(cap_drop, "ALL", f"Service '{service_name}' must declare cap_drop: [ALL].")
    assert_list_contains(security_opt, "no-new-privileges:true", f"Service '{service_name}' must declare security_opt no-new-privileges:true.")


def assert_service_cap_add_only(service_name: str, service_block: str, allowed_caps: list[str]) -> None:
    for cap in get_list_section_items(service_block, "cap_add"):
        if cap not in allowed_caps:
            fail(f"Service '{service_name}' has unsupported cap_add '{cap}'. Allowed caps: {', '.join(allowed_caps)}")


def assert_service_read_only(service_name: str, service_block: str, required_tmpfs_targets: list[str]) -> None:
    if get_scalar_value(service_block, "read_only") != "true":
        fail(f"Service '{service_name}' must set read_only: true.")
    tmpfs = get_list_section_items(service_block, "tmpfs")
    for target in required_tmpfs_targets:
        assert_list_startswith(tmpfs, target, f"Service '{service_name}' must declare tmpfs target '{target}'.")


def assert_service_has_writable_volumes(service_name: str, service_block: str, required_targets: list[str]) -> None:
    volumes = get_volume_list(service_block)
    for target in required_targets:
        if not any(f":{target}" in volume for volume in volumes):
            fail(f"Service '{service_name}' must declare writable volume target '{target}'.")


def assert_service_has_runtime_limits(service_name: str, service_block: str) -> None:
    for key in ["cpus", "mem_limit", "pids_limit"]:
        value = get_scalar_value(service_block, key)
        if value is None or not value.strip():
            fail(f"Service '{service_name}' must declare runtime limit '{key}'.")


def assert_service_has_healthcheck(service_name: str, service_block: str) -> None:
    if get_scalar_value(service_block, "healthcheck") is None and "\n    healthcheck:\n" not in f"{service_block}\n":
        fail(f"Service '{service_name}' must declare a healthcheck.")


def assert_no_dangerous_runtime_modes(text: str, label: str) -> None:
    assert_regex_not_match(text, r"^\s+privileged:\s*true\s*$", f"{label} must not enable privileged: true.")
    assert_regex_not_match(text, r"^\s+use_api_socket:\s*true\s*$", f"{label} must not enable use_api_socket: true.")
    assert_regex_not_match(text, r"^\s+network_mode:\s*host\s*$", f"{label} must not use network_mode: host.")
    assert_regex_not_match(text, r"^\s+pid:\s*host\s*$", f"{label} must not use pid: host.")
    assert_regex_not_match(text, r"^\s+ipc:\s*host\s*$", f"{label} must not use ipc: host.")
    assert_not_contains(text, "/var/run/docker.sock", f"{label} must not mount /var/run/docker.sock.")
    assert_regex_not_match(text, r"/(?:var/)?run/docker\.sock(?::|\s|$)", f"{label} must not mount a Docker socket through an alternate bind target.")


def invoke_compose_config_validation(root, env_file: str, compose_files: list[str]) -> None:
    if not command_exists("docker"):
        print("validate-production-runtime-baseline: docker command not found, skipping docker compose config render and relying on source checks.")
        return

    args = ["docker", "compose", "--env-file", str(root / env_file)]
    for compose_file in compose_files:
        args.extend(["-f", str(root / compose_file)])
    args.append("config")
    result = run_command(args)
    if result.returncode != 0:
        fail(f"docker compose config failed for files: {', '.join(compose_files)}")


def main() -> None:
    root = repo_root()
    base_text = read_repo_text("docker-compose.production.yml", "Production compose file")
    monitoring_text = read_repo_text("docker-compose.production.monitoring.yml", "Production monitoring compose file")
    env_example_text = read_repo_text(".env.production.example", "Production environment example")

    invoke_compose_config_validation(root, ".env.production.example", ["docker-compose.production.yml"])
    invoke_compose_config_validation(root, ".env.production.example", ["docker-compose.production.yml", "docker-compose.production.monitoring.yml"])

    assert_no_dangerous_runtime_modes(base_text, "Production compose baseline")
    assert_no_dangerous_runtime_modes(monitoring_text, "Production monitoring overlay")

    jvb_block = get_service_block(base_text, "jitsi-jvb")
    assert_contains(
        jvb_block,
        "JVB_ADVERTISE_IPS=${JVB_ADVERTISE_IPS:?set JVB_ADVERTISE_IPS to the public NAT address}",
        "JVB must require the public NAT address explicitly so WebRTC candidates do not advertise a private container address.",
    )
    assert_contains(
        env_example_text,
        "JVB_ADVERTISE_IPS=203.0.113.10",
        "Production environment example must expose the documentation-only JVB public NAT placeholder.",
    )

    keycloak_block = get_service_block(base_text, "keycloak")
    assert_contains(
        keycloak_block,
        "./pilot/keycloak/realm/production:/opt/keycloak/data/import:ro",
        "Production Keycloak must import the production-only realm directory.",
    )
    production_realm_path = root / "pilot/keycloak/realm/production/jitsi-realm.json"
    production_realm = json.loads(production_realm_path.read_text(encoding="utf-8"))
    if production_realm.get("users"):
        fail("Production Keycloak realm must not seed test users.")

    least_privilege_base_services = [
        "nginx",
        "frontend",
        "backend",
        "backend-vault-bootstrap",
        "db",
        "redis",
        "keycloak",
        "jitsi-web",
        "jitsi-prosody",
        "jitsi-jicofo",
        "jitsi-jvb",
    ]
    least_privilege_overlay_services = ["prometheus", "alertmanager", "mock-alert-receiver", "grafana"]
    read_only_services = {
        "nginx": ["/var/cache/nginx", "/var/run", "/var/log/nginx"],
        "frontend": ["/tmp"],
        "backend": ["/tmp"],
        "backend-vault-bootstrap": ["/tmp"],
        "redis": ["/data", "/tmp"],
        "keycloak": ["/tmp"],
        "jitsi-web": ["/tmp", "/var/cache/nginx", "/var/run", "/var/log/nginx"],
        "jitsi-prosody": ["/tmp"],
        "jitsi-jicofo": ["/tmp"],
        "jitsi-jvb": ["/tmp"],
        "mock-alert-receiver": ["/tmp"],
    }
    writable_volume_targets = {
        "backend-vault-bootstrap": ["/vault/runtime"],
        "keycloak": ["/opt/keycloak/data"],
        "jitsi-web": ["/config"],
        "jitsi-prosody": ["/config"],
        "jitsi-jicofo": ["/config"],
        "jitsi-jvb": ["/config"],
    }
    runtime_limited_services = ["frontend", "backend", "keycloak", "jitsi-web", "jitsi-prosody", "jitsi-jicofo", "jitsi-jvb"]
    allowed_cap_add = {"nginx": ["NET_BIND_SERVICE"], "jitsi-web": ["NET_BIND_SERVICE"]}

    for service_name in least_privilege_base_services:
        service_block = get_service_block(base_text, service_name)
        assert_service_has_least_privilege(service_name, service_block)
        assert_service_cap_add_only(service_name, service_block, allowed_cap_add.get(service_name, []))
        if service_name in read_only_services:
            assert_service_read_only(service_name, service_block, read_only_services[service_name])
        if service_name in writable_volume_targets:
            assert_service_has_writable_volumes(service_name, service_block, writable_volume_targets[service_name])
        if service_name in runtime_limited_services:
            assert_service_has_runtime_limits(service_name, service_block)
            assert_service_has_healthcheck(service_name, service_block)

    for service_name in least_privilege_overlay_services:
        service_block = get_service_block(monitoring_text, service_name)
        assert_service_has_least_privilege(service_name, service_block)
        assert_service_cap_add_only(service_name, service_block, [])
        if service_name in read_only_services:
            assert_service_read_only(service_name, service_block, read_only_services[service_name])

    print("validate-production-runtime-baseline: OK")
    print("validate-production-runtime-baseline: verified least-privilege defaults, read-only targets, runtime limits and docker-socket prohibition")
    print("validate-production-runtime-baseline: verified production-only Keycloak import and required JVB public NAT advertisement")


if __name__ == "__main__":
    main()
