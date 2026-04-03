from __future__ import annotations

from _python_guardrails import assert_contains, assert_not_contains, assert_regex, fail, get_service_block, read_repo_text, repo_root


def main() -> None:
    root = repo_root()
    compose_text = read_repo_text("docker-compose.production.yml")
    env_example_text = read_repo_text(".env.production.example")
    delivery_readme = read_repo_text("deploy/vault/delivery/README.md")
    backend_fetch = read_repo_text("deploy/vault/auth/backend/startup-fetch.sh.example")
    backend_run_bridge = read_repo_text("deploy/vault/auth/backend/run-with-rendered-secrets.sh.example")
    vault_governance_matrix = read_repo_text("deploy/vault/secret-governance-matrix.md")

    for secret_var in [
        "APP_MEETINGS_TOKEN_SIGNING_SECRET=",
        "SSO_CLIENT_SECRET=",
        "SPRING_DATASOURCE_PASSWORD=",
        "POSTGRES_PASSWORD=",
        "KEYCLOAK_ADMIN_PASSWORD=",
        "JWT_APP_SECRET=",
        "JICOFO_AUTH_PASSWORD=",
        "JVB_AUTH_PASSWORD=",
        "JICOFO_COMPONENT_SECRET=",
    ]:
        assert_not_contains(env_example_text, secret_var, f".env.production.example must not carry secret value placeholder '{secret_var}'.")

    for path_hint in [
        "POSTGRES_VAULT_ENV_FILE_PATH=",
        "REDIS_VAULT_ENV_FILE_PATH=",
        "KEYCLOAK_VAULT_ENV_FILE_PATH=",
        "JITSI_WEB_VAULT_ENV_FILE_PATH=",
        "JITSI_PROSODY_VAULT_ENV_FILE_PATH=",
        "JITSI_JICOFO_VAULT_ENV_FILE_PATH=",
        "JITSI_JVB_VAULT_ENV_FILE_PATH=",
    ]:
        assert_contains(env_example_text, path_hint, f".env.production.example must keep service-specific Vault delivery path hint '{path_hint}'.")

    backend = get_service_block(compose_text, "backend")
    backend_bootstrap = get_service_block(compose_text, "backend-vault-bootstrap")
    frontend = get_service_block(compose_text, "frontend")
    db = get_service_block(compose_text, "db")
    redis = get_service_block(compose_text, "redis")
    keycloak = get_service_block(compose_text, "keycloak")
    jitsi_web = get_service_block(compose_text, "jitsi-web")
    jitsi_prosody = get_service_block(compose_text, "jitsi-prosody")
    jitsi_jicofo = get_service_block(compose_text, "jitsi-jicofo")
    jitsi_jvb = get_service_block(compose_text, "jitsi-jvb")

    for service_block in [backend, db, keycloak, jitsi_web, jitsi_prosody, jitsi_jicofo, jitsi_jvb]:
        assert_not_contains(service_block, "secret_net", "Only vault and backend-vault-bootstrap may stay on secret_net in the default delivery baseline.")
    assert_not_contains(frontend, "VAULT_ADDR=", "Frontend/browser path must not receive direct Vault access.")

    for needle in ["SPRING_DATASOURCE_PASSWORD=${", "APP_MEETINGS_TOKEN_SIGNING_SECRET=${", "SSO_CLIENT_SECRET=${"]:
        assert_not_contains(backend, needle, f"Backend must not depend on long-lived secret env '{needle}'.")

    assert_contains(backend, "run-with-rendered-secrets.sh.example", "Backend must use the committed pre-start secret bridge.")
    assert_contains(backend, "backend-vault-runtime:/vault/runtime", "Backend must consume a bounded runtime volume instead of a global .env file.")
    assert_contains(backend_bootstrap, "BACKEND_ENV_OUTPUT_FILE=/vault/runtime/backend/runtime.env", "backend-vault-bootstrap must render backend runtime env output.")
    assert_contains(backend_bootstrap, "OUTPUT_UID=10001", "backend-vault-bootstrap must keep ownership aligned with the backend runtime user.")

    for service_block, token, name in [
        (db, "POSTGRES_VAULT_ENV_FILE_PATH", "PostgreSQL"),
        (redis, "REDIS_VAULT_ENV_FILE_PATH", "Redis"),
        (keycloak, "KEYCLOAK_VAULT_ENV_FILE_PATH", "Keycloak"),
        (jitsi_web, "JITSI_WEB_VAULT_ENV_FILE_PATH", "Jitsi Web"),
        (jitsi_prosody, "JITSI_PROSODY_VAULT_ENV_FILE_PATH", "Jitsi Prosody"),
        (jitsi_jicofo, "JITSI_JICOFO_VAULT_ENV_FILE_PATH", "Jitsi Jicofo"),
        (jitsi_jvb, "JITSI_JVB_VAULT_ENV_FILE_PATH", "Jitsi JVB"),
    ]:
        assert_regex(service_block, token, f"{name} must consume a service-specific Vault delivery env file.")

    assert_contains(redis, "--requirepass", "Redis baseline must enforce a Vault-delivered password instead of anonymous runtime access.")
    assert_regex(redis, r'redis-cli --no-auth-warning -a \\"\$\$?REDIS_PASSWORD\\" ping \| grep -q PONG', "Redis healthcheck must authenticate with the Vault-delivered password.")

    for needle, message in [
        ("database/static-creds/backend-app", "Backend fetch path must prefer static-role DB credentials for the current long-lived runtime."),
        ("database/creds/backend-app", "Backend fetch path must keep an explicit dynamic-credentials fallback contract."),
        ('rm -f "$TOKEN_SINK_FILE"', "Backend fetch path must clean the temporary token sink after handoff."),
        ('chmod 0600 "$BACKEND_ENV_OUTPUT_FILE"', "Backend fetch path must lock runtime bridge permissions explicitly."),
        ('chown "$OUTPUT_UID:$OUTPUT_GID" "$BACKEND_ENV_OUTPUT_FILE"', "Backend fetch path must align runtime bridge ownership with the target consumer."),
    ]:
        assert_contains(backend_fetch, needle, message)
    assert_not_contains(backend_run_bridge, 'rm -f "$SECRETS_FILE"', "Backend runtime bridge must keep the rendered env file in the bounded runtime volume so ordinary backend restarts stay restart-safe.")

    for needle, message in [
        ("controlled restart/redeploy", "Delivery README must document restart-based rotation contracts."),
        ("examples/*.env.example", "Delivery README must explain that committed example env files are not production source of truth."),
        ("response-wrapped secret_id", "Delivery README must document wrapped secret_id handling as a sensitive delivery artifact."),
        ("token sink", "Delivery README must document token sink lifecycle and cleanup expectations."),
        ("0600", "Delivery README must document strict file permissions for rendered runtime artifacts."),
        ("ProblemDetail", "Delivery README must forbid leaking secret artifacts into ProblemDetail payloads."),
        ("startup diagnostics", "Delivery README must forbid leaking secret artifacts into startup diagnostics."),
        ("lease dumps", "Delivery README must forbid treating lease dumps as ordinary troubleshooting notes."),
        ("immediately after handoff", "Delivery README must document which artifacts require immediate cleanup after handoff."),
        ("next controlled restart/redeploy", "Delivery README must document which rendered files may survive until the next controlled restart/redeploy."),
    ]:
        assert_contains(delivery_readme, needle, message)
    assert_contains(vault_governance_matrix, "controlled restart/redeploy", "Governance matrix must align delivery surfaces with explicit runtime contracts.")

    for example_file in [
        "deploy/vault/delivery/examples/postgres.env.example",
        "deploy/vault/delivery/examples/redis.env.example",
        "deploy/vault/delivery/examples/keycloak.env.example",
        "deploy/vault/delivery/examples/jitsi-web.env.example",
        "deploy/vault/delivery/examples/jitsi-prosody.env.example",
        "deploy/vault/delivery/examples/jitsi-jicofo.env.example",
        "deploy/vault/delivery/examples/jitsi-jvb.env.example",
    ]:
        example_text = read_repo_text(example_file)
        assert_contains(example_text, "SET_IN_VAULT_RENDERED_FILE_ONLY", f"{example_file} must stay a non-secret placeholder file only.")

    print("validate-production-secret-delivery-baseline: OK")
    print("validate-production-secret-delivery-baseline: verified service-specific Vault delivery files and backend bounded runtime bridge")


if __name__ == "__main__":
    main()
