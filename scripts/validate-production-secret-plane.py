from __future__ import annotations

from pathlib import Path

from _python_guardrails import (
    assert_contains,
    assert_list_contains,
    assert_list_has_regex,
    assert_not_contains,
    assert_regex,
    fail,
    get_list_section_items,
    get_scalar_value,
    get_service_block,
    read_text,
    repo_root,
)


def main() -> None:
    root = repo_root()
    compose_text = read_text(root / "docker-compose.production.yml")
    deployment_guide = read_text(root / "docs/deployment-production.md")
    root_readme = read_text(root / "README.md")
    host_operator_model = read_text(root / "deploy/host/operator-access-model.md")
    vault_baseline_readme_path = root / "deploy/vault/README.md"
    vault_config_path = root / "deploy/vault/config/vault.hcl.example"
    vault_audit_bootstrap_path = root / "deploy/vault/bootstrap/enable-audit-file.sh.example"
    vault_governance_matrix_path = root / "deploy/vault/secret-governance-matrix.md"
    vault_break_glass_runbook_path = root / "deploy/vault/break-glass-runbook.md"

    vault_service = get_service_block(compose_text, "vault")
    vault_networks = get_list_section_items(vault_service, "networks")
    vault_ports = get_list_section_items(vault_service, "ports")
    vault_volumes = get_list_section_items(vault_service, "volumes")
    vault_cap_drop = get_list_section_items(vault_service, "cap_drop")
    vault_security_opt = get_list_section_items(vault_service, "security_opt")

    if vault_ports:
        fail(f"Vault must remain internal-only and must not publish host ports. Found: {', '.join(vault_ports)}")

    for required_network in ["secret_net", "ops_net"]:
        assert_list_contains(vault_networks, required_network, f"Vault must be attached to '{required_network}'. Current networks: {', '.join(vault_networks)}")
    for forbidden_network in ["edge_net", "app_net", "identity_net", "realtime_net", "data_net"]:
        if forbidden_network in vault_networks:
            fail(f"Vault must not be attached to '{forbidden_network}'. Current networks: {', '.join(vault_networks)}")

    for service_name in ["nginx", "frontend", "keycloak"]:
        service_networks = get_list_section_items(get_service_block(compose_text, service_name), "networks")
        if "secret_net" in service_networks:
            fail(f"Service '{service_name}' must not have secret_net access in the shared Vault baseline.")

    backend_service = get_service_block(compose_text, "backend")
    if "secret_net" in get_list_section_items(backend_service, "networks"):
        fail("Backend must not join secret_net directly in Story 19.2; only the backend-scoped bootstrap helper may do so.")

    backend_bootstrap_service = get_service_block(compose_text, "backend-vault-bootstrap")
    assert_list_contains(get_list_section_items(backend_bootstrap_service, "networks"), "secret_net", "Backend bootstrap helper must be attached to secret_net.")
    assert_contains(backend_bootstrap_service, "/vault/auth/backend/startup-fetch.sh.example", "Backend bootstrap helper must run the committed startup-fetch script.")

    if "ALL" not in vault_cap_drop:
        fail("Vault must declare cap_drop: [ALL].")
    if "no-new-privileges:true" not in vault_security_opt:
        fail("Vault must declare security_opt no-new-privileges:true.")

    assert_regex(vault_service, r"^    build:\s*$", "Vault must use a repo-kept build path instead of pulling an unmanaged runtime image directly.")
    assert_contains(vault_service, "context: ./deploy/vault", "Vault build must use deploy/vault as build context.")
    assert_contains(vault_service, "VAULT_VERSION: 1.21.4", "Vault build must pin the expected stable release version.")
    assert_contains(vault_service, "VAULT_ZIP_URL: https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_linux_amd64.zip", "Vault build must pull the binary from the approved Yandex mirror artifact path.")
    assert_contains(vault_service, "VAULT_SHA256SUMS_URL: https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_SHA256SUMS", "Vault build must validate checksums from the approved Yandex mirror checksum source.")
    assert_contains(vault_service, "image: jitsi-vault:1.21.4", "Vault runtime image tag must stay aligned with the committed stable release.")

    assert_list_has_regex(vault_volumes, r"deploy/vault/config/vault\.hcl\.example:/vault/config/vault\.hcl:ro$", "Vault compose baseline must mount the committed Vault config template read-only.")
    assert_list_has_regex(vault_volumes, r"deploy/vault/bootstrap:/vault/bootstrap:ro$", "Vault compose baseline must mount the committed audit/bootstrap directory read-only.")
    assert_list_has_regex(vault_volumes, r"vault-data:/vault/data$", "Vault compose baseline must mount a dedicated data volume.")
    assert_list_has_regex(vault_volumes, r"vault-audit:/vault/audit$", "Vault compose baseline must mount a dedicated audit volume.")
    assert_contains(vault_service, 'command: ["server", "-config=/vault/config/vault.hcl"]', "Vault compose baseline must start Vault with the committed config path.")

    if get_scalar_value(vault_service, "read_only") != "true":
        fail("Vault must set read_only: true.")
    if get_scalar_value(vault_service, "healthcheck") is None and "\n    healthcheck:\n" not in f"{vault_service}\n":
        fail("Vault must define a healthcheck.")

    for path, message in [
        (vault_baseline_readme_path, "deploy/vault/README.md must document the Vault baseline, approved mirror and audit evidence path."),
        (vault_config_path, "deploy/vault/config/vault.hcl.example must exist as repo-kept Vault baseline config."),
        (vault_audit_bootstrap_path, "deploy/vault/bootstrap/enable-audit-file.sh.example must exist as repo-kept audit bootstrap baseline."),
        (vault_governance_matrix_path, "deploy/vault/secret-governance-matrix.md must exist as the repo-kept governance matrix for Story 19.4."),
        (vault_break_glass_runbook_path, "deploy/vault/break-glass-runbook.md must exist as the repo-kept break-glass and recovery separation runbook."),
        (root / "deploy/vault/Dockerfile", "deploy/vault/Dockerfile must exist as repo-kept build-from-mirror runtime definition."),
    ]:
        if not path.exists():
            fail(message)

    vault_baseline_readme = read_text(vault_baseline_readme_path)
    vault_config_text = read_text(vault_config_path)
    vault_audit_bootstrap = read_text(vault_audit_bootstrap_path)
    vault_governance_matrix = read_text(vault_governance_matrix_path)
    vault_break_glass_runbook = read_text(vault_break_glass_runbook_path)
    vault_dockerfile = read_text(root / "deploy/vault/Dockerfile")

    for needle, message in [
        ("https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/", "Vault baseline must document the approved Yandex mirror path."),
        ("vault_1.21.4_SHA256SUMS", "Vault baseline must document the checksum source for the pinned release."),
        ("audit device", "Vault baseline must require audit devices."),
        ("private path, bastion or VPN", "Vault baseline must document operator access through a private path, bastion or VPN only."),
        ("non-HA", "Vault baseline must document the single-node non-HA compromise for this stage."),
        ("Story 19.2", "Vault baseline must declare auth/policy mapping as out of scope for this story."),
        ("Story 19.3", "Vault baseline must declare production secret migration as out of scope for this story."),
        ("Epic 20", "Vault baseline must declare backup/restore and incident response as out of scope for this story."),
        ("Story 19.4", "Vault baseline must document Story 19.4 as the governance layer for rotation and break-glass rules."),
        ("secret-governance-matrix.md", "Vault baseline must reference the repo-kept secret governance matrix."),
        ("break-glass-runbook.md", "Vault baseline must reference the repo-kept break-glass runbook."),
        ("governance layer", "Vault baseline must frame Story 19.4 as governance rather than a new delivery redesign."),
        ("HA/TLS redesign", "Vault baseline must keep HA/TLS redesign outside the Story 19.4 scope."),
    ]:
        assert_contains(vault_baseline_readme, needle, message)
    assert_regex(vault_baseline_readme, r"vault/\d+\.\d+\.\d+", "Vault baseline must pin an exact stable release artifact path.")

    for needle, message in [
        ("backend JWT signing secret", "Secret governance matrix must include backend JWT signing material."),
        ("backend OIDC client secret", "Secret governance matrix must include backend OIDC client secret ownership."),
        ("DB credentials", "Secret governance matrix must include DB credentials."),
        ("Keycloak admin/bootstrap secret", "Secret governance matrix must include Keycloak admin/bootstrap secret."),
        ("Jitsi shared secrets", "Secret governance matrix must include Jitsi shared secrets."),
        ("backup-related credentials", "Secret governance matrix must include backup-related credentials."),
        ("TLS certificates", "Secret governance matrix must include TLS certificate governance."),
        ("single source of truth remains Vault path version", "Secret governance matrix must forbid parallel source-of-truth patterns outside Vault."),
        ("ad hoc редактирования host env/config файлов", "Secret governance matrix must explicitly ban ad hoc host env/config edits as rotation workflow."),
    ]:
        assert_contains(vault_governance_matrix, needle, message)

    for needle, message in [
        ("approval path", "Break-glass runbook must document an approval path."),
        ("post-use rotation", "Break-glass runbook must require post-use rotation expectations."),
        ("emergency root shell", "Break-glass runbook must treat emergency root shell as a distinct audited path."),
        (".env.production*", "Break-glass runbook must keep recovery material out of repo-managed env files."),
        ("compose mounts", "Break-glass runbook must keep recovery material out of compose mounts."),
        ("shared local override areas", "Break-glass runbook must forbid storing recovery material in shared local override areas."),
    ]:
        assert_contains(vault_break_glass_runbook, needle, message)

    assert_contains(vault_config_text, 'storage "raft"', "Vault baseline config must use integrated Raft storage for the single-node stage.")
    assert_contains(vault_config_text, 'listener "tcp"', "Vault baseline config must declare a TCP listener.")
    assert_contains(vault_config_text, "telemetry {", "Vault baseline config must expose a telemetry block for ops path integration.")
    approved_alpine_base = "FROM alpine:3.22.5@sha256:14358309a308569c32bdc37e2e0e9694be33a9d99e68afb0f5ff33cc1f695dce"
    if vault_dockerfile.count(approved_alpine_base) != 2:
        fail("Vault Dockerfile must pin both build stages to the approved Alpine 3.22.5 manifest digest.")
    assert_contains(vault_dockerfile, "VAULT_ZIP_URL=https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_linux_amd64.zip", "Vault Dockerfile must default to the approved mirror artifact path.")
    assert_contains(vault_dockerfile, "VAULT_SHA256SUMS_URL=https://mirror.yandex.ru/mirrors/releases.hashicorp.com/vault/1.21.4/vault_1.21.4_SHA256SUMS", "Vault Dockerfile must default to the approved checksum source.")
    assert_contains(vault_dockerfile, 'grep " vault_${VAULT_VERSION}_linux_amd64.zip$" /tmp/vault_SHA256SUMS | sha256sum -c -', "Vault Dockerfile must verify the downloaded archive against the checksum list.")
    assert_contains(vault_audit_bootstrap, "vault audit enable file", "Vault audit bootstrap must enable a dedicated file audit device.")
    assert_contains(vault_audit_bootstrap, "/vault/audit/vault-audit.log", "Vault audit bootstrap must write audit events into a dedicated audit path.")
    assert_contains(vault_audit_bootstrap, "vault audit list", "Vault audit bootstrap must include an audit verification command.")

    for needle, message in [
        ("Vault internal-only secret zone", "Deployment guide must document Vault as an internal-only secret zone."),
        ("npm run prod:secret:baseline:validate", "Deployment guide must expose the secret-plane validator command."),
        ("non-secret config и path hints", "Deployment guide must explain that repo-managed env files now keep only non-secret config and path hints."),
        ("exact stable artifact path", "Deployment guide must explicitly mention the exact stable artifact path in the Vault rollout baseline."),
        ("checksum source", "Deployment guide must explicitly mention the checksum source in the Vault rollout baseline."),
        ("private path, bastion или VPN", "Deployment guide must explicitly mention the private operator path constraint."),
        ("enable-audit-file.sh.example", "Deployment guide must reference the committed audit bootstrap script name."),
        ("port vault 8200", "Deployment guide must include an evidence step that proves Vault has no published port."),
        ("Vault unseal/recovery material", "Deployment guide must mention operator-path separation for Vault recovery material."),
        ("secret-governance-matrix.md", "Deployment guide must reference the canonical secret governance matrix."),
        ("break-glass-runbook.md", "Deployment guide must reference the canonical break-glass runbook."),
        ("rotation governance", "Deployment guide must mention the rotation governance layer introduced in Story 19.4."),
    ]:
        assert_contains(deployment_guide, needle, message)

    for needle, message in [
        ("npm run prod:secret:baseline:validate", "README must expose the secret-plane validator command."),
        ("non-secret config и path hints", "README must explain that repo-managed env files now keep only non-secret config and path hints."),
        ("deploy/vault/Dockerfile", "README must mention the committed Vault build-from-mirror runtime definition."),
        ("approved Yandex mirror artifact path", "README must mention the approved Yandex mirror artifact path."),
        ("secret-governance-matrix.md", "README must reference the canonical secret governance matrix."),
        ("break-glass-runbook.md", "README must reference the canonical break-glass runbook."),
    ]:
        assert_contains(root_readme, needle, message)
    assert_contains(host_operator_model, "Vault unseal or recovery material", "Operator access model must keep Vault recovery material separate from deploy access.")

    print("validate-production-secret-plane: OK")
    print("validate-production-secret-plane: verified Vault stays internal-only on secret_net and ops_net")
    print("validate-production-secret-plane: verified approved mirror policy, audit baseline and operator-path separation notes")


if __name__ == "__main__":
    main()
