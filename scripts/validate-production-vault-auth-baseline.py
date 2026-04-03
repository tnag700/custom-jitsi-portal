from __future__ import annotations

from _python_guardrails import assert_contains, assert_not_contains, fail, get_list_section_items, get_service_block, read_repo_text, repo_root


def main() -> None:
    root = repo_root()
    compose_text = read_repo_text("docker-compose.production.yml")
    package_json = read_repo_text("package.json")
    vault_readme = read_repo_text("deploy/vault/README.md")
    auth_readme = read_repo_text("deploy/vault/auth/README.md")
    deployment_guide = read_repo_text("docs/deployment-production.md")
    root_readme = read_repo_text("README.md")
    vault_break_glass_runbook = read_repo_text("deploy/vault/break-glass-runbook.md")

    backend = get_service_block(compose_text, "backend")
    if "secret_net" in get_list_section_items(backend, "networks"):
        fail("Backend must not be attached to secret_net directly; Story 19.2 uses a backend-scoped bootstrap helper for the canonical startup-fetch path.")

    backend_bootstrap = get_service_block(compose_text, "backend-vault-bootstrap")
    if "secret_net" not in get_list_section_items(backend_bootstrap, "networks"):
        fail("backend-vault-bootstrap must be attached to secret_net as the canonical backend-scoped Vault client.")

    for needle, message in [
        ("VAULT_ADDR=http://vault:8200", "Backend bootstrap helper must pin Vault private address for startup fetch examples."),
        ("ROLE_ID_FILE=/vault/local/backend/role_id", "Backend bootstrap helper must read the backend role_id from a private handoff path."),
        ("WRAPPED_SECRET_ID_FILE=/vault/local/backend/wrapped-secret-id", "Backend bootstrap helper must read the wrapped secret_id from a private handoff path."),
        ("TOKEN_SINK_FILE=/vault/runtime/backend-batch-token", "Backend bootstrap helper must emit the bounded token to the committed runtime sink path."),
        ("/vault/auth/backend/startup-fetch.sh.example", "Backend bootstrap helper must run the committed startup-fetch script."),
    ]:
        assert_contains(backend_bootstrap, needle, message)
    assert_contains(backend, "backend-vault-bootstrap:", "Backend compose baseline must wait for the bootstrap helper before startup.")

    for service_name in ["nginx", "frontend", "keycloak", "jitsi-web", "jitsi-prosody", "jitsi-jicofo", "jitsi-jvb"]:
        service_block = get_service_block(compose_text, service_name)
        if "secret_net" in get_list_section_items(service_block, "networks"):
            fail(f"Service '{service_name}' must not become a default Vault client in Story 19.2.")
        for forbidden_needle in ["VAULT_ADDR=", "APP_VAULT_ADDR=", "APP_VAULT_AUTH_MODE=", "APP_VAULT_AUTH_PATH=", "APP_VAULT_ROLE_NAME="]:
            assert_not_contains(service_block, forbidden_needle, f"Service '{service_name}' must not receive Vault runtime wiring by default.")

    required_artifacts = [
        "deploy/vault/auth/README.md",
        "deploy/vault/auth/backend/approle-role.json.example",
        "deploy/vault/auth/backend/startup-fetch.sh.example",
        "deploy/vault/auth/backup/approle-role.json.example",
        "deploy/vault/auth/backup/startup-fetch.sh.example",
        "deploy/vault/auth/operators/oidc-config.json.example",
        "deploy/vault/auth/operators/oidc-role.json.example",
        "deploy/vault/policies/backend.hcl.example",
        "deploy/vault/policies/keycloak.hcl.example",
        "deploy/vault/policies/jitsi.hcl.example",
        "deploy/vault/policies/backup-runner.hcl.example",
        "deploy/vault/policies/operator-day2-admin.hcl.example",
        "deploy/vault/bootstrap/issue-backend-wrapped-secret-id.sh.example",
        "deploy/vault/bootstrap/issue-backup-wrapped-secret-id.sh.example",
    ]
    for artifact in required_artifacts:
        if not (root / artifact).exists():
            fail(f"Required auth baseline artifact is missing: {root / artifact}")

    backend_policy = read_repo_text("deploy/vault/policies/backend.hcl.example")
    keycloak_policy = read_repo_text("deploy/vault/policies/keycloak.hcl.example")
    jitsi_policy = read_repo_text("deploy/vault/policies/jitsi.hcl.example")
    backup_policy = read_repo_text("deploy/vault/policies/backup-runner.hcl.example")
    operator_policy = read_repo_text("deploy/vault/policies/operator-day2-admin.hcl.example")
    oidc_role = read_repo_text("deploy/vault/auth/operators/oidc-role.json.example")
    startup_fetch = read_repo_text("deploy/vault/auth/backend/startup-fetch.sh.example")
    backend_approle = read_repo_text("deploy/vault/auth/backend/approle-role.json.example")
    backup_approle = read_repo_text("deploy/vault/auth/backup/approle-role.json.example")
    backend_wrapped_issue = read_repo_text("deploy/vault/bootstrap/issue-backend-wrapped-secret-id.sh.example")
    backup_wrapped_issue = read_repo_text("deploy/vault/bootstrap/issue-backup-wrapped-secret-id.sh.example")

    for needle, message in [
        ("kv/data/app/backend/*", "Backend policy must stay inside the backend app contour."),
        ("kv/data/app/redis/*", "Backend policy must declare bounded Redis credential access explicitly."),
        ("kv/data/identity/backend/*", "Backend policy must document bounded identity-related access."),
        ("kv/data/backup/backend/*", "Backend policy must document bounded backup-related access."),
        ("database/creds/backend-app", "Backend policy must keep database secrets engine as the preferred DB credential target."),
        ("database/static-creds/backend-app", "Backend policy must document transitional static-role DB access for current long-lived consumers."),
    ]:
        assert_contains(backend_policy, needle, message)
    for forbidden in ["sys/*", "auth/*", "kv/*"]:
        assert_not_contains(backend_policy, forbidden, f"Backend policy must not grant broad operator-level access via '{forbidden}'.")

    assert_contains(keycloak_policy, "kv/data/identity/keycloak/*", "Keycloak mapping must stay inside identity/keycloak contour.")
    assert_contains(jitsi_policy, "kv/data/realtime/jitsi/*", "Jitsi mapping must stay inside realtime/jitsi contour.")
    assert_contains(backup_policy, "kv/data/backup/runner/*", "Backup runner policy must stay inside backup contour.")
    assert_contains(backup_policy, "database/creds/backup-job", "Backup runner policy must declare DB credential target explicitly.")
    assert_contains(backup_policy, "database/static-creds/backup-job", "Backup runner policy must document transitional static-role DB access explicitly.")
    assert_contains(keycloak_policy, "database/static-creds/keycloak-app", "Keycloak policy must document transitional static-role DB access explicitly.")
    assert_not_contains(keycloak_policy, "kv/data/app/", "Keycloak mapping must not read backend app secrets.")
    assert_not_contains(jitsi_policy, "kv/data/app/", "Jitsi mapping must not read backend app secrets.")

    assert_contains(operator_policy, "auth/token/lookup-self", "Operator policy must be least-privilege and self-scoped.")
    for forbidden in ["sys/raw", "sys/seal", "kv/metadata/*"]:
        assert_not_contains(operator_policy, forbidden, f"Operator policy must not include '{forbidden}'.")

    for needle, message in [
        ("127.0.0.1:8250/oidc/callback", "OIDC baseline must document CLI redirect URI."),
        ("/ui/vault/auth/auth/oidc-operators/oidc/callback", "OIDC baseline must document UI redirect URI."),
        ("bound_audiences", "OIDC baseline must keep bound_audiences explicit for Vault 1.17+ behavior."),
        ("bound_claims", "OIDC baseline must require an explicit operator group binding."),
    ]:
        assert_contains(oidc_role, needle, message)

    for content, ttl, label in [
        (backend_approle, "10m", "Backend AppRole baseline"),
        (backup_approle, "15m", "Backup AppRole baseline"),
    ]:
        assert_contains(content, '"token_type": "batch"', f"{label} must keep batch tokens.")
        assert_contains(content, '"secret_id_num_uses": 1', f"{label} must enforce one-use secret_id semantics.")
        assert_contains(content, f'"secret_id_ttl": "{ttl}"', f"{label} must declare secret_id TTL explicitly.")
    assert_contains(backend_wrapped_issue, "-wrap-ttl=5m", "Backend wrapped secret_id issuance must keep a bounded wrap TTL.")
    assert_contains(backup_wrapped_issue, "-wrap-ttl=5m", "Backup wrapped secret_id issuance must keep a bounded wrap TTL.")
    assert_contains(startup_fetch, "vault unwrap -field=secret_id", "Backend startup fetch example must unwrap a wrapped secret_id.")
    assert_contains(startup_fetch, "auth/approle-workloads/login", "Backend startup fetch example must log in through the scoped AppRole mount.")

    for needle, message in [
        ("database/creds/*", "Auth README must document database secrets engine target paths."),
        ("database/static-creds/*", "Auth README must document transitional static-role DB target paths."),
        ("Frontend SSR и browser runtime не становятся Vault clients по умолчанию.", "Auth README must keep browser and SSR outside the default Vault client model."),
        ("Keycloak и Jitsi не становятся direct Vault clients автоматически.", "Auth README must keep Keycloak and Jitsi on reviewable mapping rules only."),
        ("backend-scoped bootstrap helper container", "Auth README must describe the runnable backend bootstrap helper path."),
        ("secret_id_num_uses=1", "Auth README must document one-use secret_id semantics explicitly."),
        ("wrap TTL 5m", "Auth README must document bounded wrap TTL for secret_id handoff."),
        ("day-2 operators не получают implicit access к sys/seal", "Auth README must state that day-2 operators do not receive recovery-level capabilities."),
        ("recovery actor", "Auth README must reference the separate recovery actor model."),
    ]:
        assert_contains(auth_readme, needle, message)

    for needle, message in [
        ("backend-service", "Vault README must document the scoped backend identity."),
        ("auth/approle-workloads", "Vault README must document the workload AppRole mount."),
        ("auth/oidc-operators", "Vault README must document the operator OIDC mount."),
        ("backend-vault-bootstrap", "Vault README must document the backend-scoped bootstrap helper service."),
        ("Story 19.3 переводит фактическую delivery модель", "Vault README must document the Story 19.3 delivery cutover."),
    ]:
        assert_contains(vault_readme, needle, message)
    assert_contains(vault_break_glass_runbook, "day-2 Vault operator", "Break-glass runbook must distinguish day-2 Vault operators from recovery actors.")

    for needle, message in [
        ("prod:secret:auth:validate", "Deployment guide must expose the focused auth validator."),
        ("response-wrapped AppRole handoff", "Deployment guide must document the backend canonical AppRole handoff."),
        ("database secrets engine", "Deployment guide must document the DB engine decision explicitly."),
        ("backend-vault-bootstrap", "Deployment guide must document the backend-scoped bootstrap helper service."),
        ("secret_id_num_uses=1", "Deployment guide must mention one-use AppRole handoff semantics."),
    ]:
        assert_contains(deployment_guide, needle, message)

    assert_contains(root_readme, "prod:secret:auth:validate", "README must expose the focused auth validator.")
    assert_contains(root_readme, "frontend SSR по умолчанию не становится Vault client", "README must keep SSR out of the default Vault client model.")
    assert_contains(package_json, "prod:secret:auth:validate", "package.json must expose the focused auth validator entry point.")

    print("validate-production-vault-auth-baseline: OK")
    print("validate-production-vault-auth-baseline: verified backend-scoped AppRole startup fetch helper, least-privilege policy templates and operator OIDC mapping")


if __name__ == "__main__":
    main()
