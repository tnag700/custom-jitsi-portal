from __future__ import annotations

import json

from _keycloak_guardrails import validate_portal_user_profile
from _python_guardrails import fail, repo_root


def main() -> None:
    root = repo_root()
    realm_path = root / "pilot/keycloak/realm/dev/jitsi-dev-realm.json"
    compose_path = root / "docker-compose.yml"
    dev_vault_bootstrap_path = root / "deploy/vault/auth/dev/bootstrap-dev-vault.sh"
    jitsi_web_access_path = root / "pilot/jitsi/web/custom-meet.development.conf"

    if not realm_path.exists():
        fail(f"Keycloak realm file not found: {realm_path}")

    if not compose_path.exists():
        fail(f"Compose file not found: {compose_path}")

    if not dev_vault_bootstrap_path.exists():
        fail(f"Development Vault bootstrap file not found: {dev_vault_bootstrap_path}")

    if not jitsi_web_access_path.exists():
        fail(f"Development Jitsi web access file not found: {jitsi_web_access_path}")

    realm = json.loads(realm_path.read_text(encoding="utf-8"))
    users = list(realm.get("users") or [])
    validate_portal_user_profile(
        realm,
        label="Development Keycloak realm",
        require_seeded_tenants=True,
    )

    if not users:
        fail("Keycloak realm must define at least one seeded user.")

    users_missing_id = [user for user in users if not str(user.get("id") or "").strip()]
    if users_missing_id:
        usernames = ", ".join(str(user.get("username") or "") for user in users_missing_id)
        fail(f"All seeded Keycloak users must define explicit stable ids. Missing for: {usernames}")

    seen_ids: dict[str, int] = {}
    for user in users:
        identifier = str(user.get("id"))
        seen_ids[identifier] = seen_ids.get(identifier, 0) + 1

    duplicates = [identifier for identifier, count in seen_ids.items() if count > 1]
    if duplicates:
        fail(f"Seeded Keycloak user ids must be unique. Duplicate ids: {', '.join(duplicates)}")

    compose_text = compose_path.read_text(encoding="utf-8")
    dev_vault_bootstrap_text = dev_vault_bootstrap_path.read_text(encoding="utf-8")
    package_lock = json.loads((root / "frontend-qwik/package-lock.json").read_text(encoding="utf-8"))
    locked_packages = package_lock.get("packages") or {}
    application_config = (root / "backend/src/main/resources/application.yml").read_text(encoding="utf-8")
    framework_version_contract = {
        "Qwik": (
            locked_packages["node_modules/@qwik.dev/core"]["version"],
            "APP_VERSION_MONITOR_QWIK_VERSION",
        ),
        "Qwik Router": (
            locked_packages["node_modules/@qwik.dev/router"]["version"],
            "APP_VERSION_MONITOR_QWIK_ROUTER_VERSION",
        ),
        "Express": (
            locked_packages["node_modules/express"]["version"],
            "APP_VERSION_MONITOR_EXPRESS_VERSION",
        ),
        "Qwik UI": (
            locked_packages["node_modules/@qwik-ui/headless"]["version"],
            "APP_VERSION_MONITOR_QWIK_UI_VERSION",
        ),
        "Vite": (
            locked_packages["node_modules/vite"]["version"],
            "APP_VERSION_MONITOR_VITE_VERSION",
        ),
        "TypeScript": (
            locked_packages["node_modules/typescript"]["version"],
            "APP_VERSION_MONITOR_TYPESCRIPT_VERSION",
        ),
        "Tailwind CSS": (
            locked_packages["node_modules/tailwindcss"]["version"],
            "APP_VERSION_MONITOR_TAILWIND_VERSION",
        ),
        "Vitest": (
            locked_packages["node_modules/vitest"]["version"],
            "APP_VERSION_MONITOR_VITEST_VERSION",
        ),
        "ESLint": (
            locked_packages["node_modules/eslint"]["version"],
            "APP_VERSION_MONITOR_ESLINT_VERSION",
        ),
    }
    for label, (version, environment_name) in framework_version_contract.items():
        expected_default = f"${{{environment_name}:{version}}}"
        if expected_default not in application_config:
            fail(
                f"{label} CVE inventory version must match package-lock.json. "
                f"Expected application.yml to contain '{expected_default}'."
            )

    if "- pgdata:/var/lib/postgresql\n" not in compose_text and "- pgdata:/var/lib/postgresql\r\n" not in compose_text:
        fail("docker-compose.yml must mount pgdata to /var/lib/postgresql for Postgres 18 image layout compatibility and data preservation across recreates.")

    if "- pgdata:/var/lib/postgresql/data" in compose_text:
        fail("docker-compose.yml still contains legacy pgdata mount to /var/lib/postgresql/data. Use /var/lib/postgresql with Postgres 18 images.")

    required_fragments = {
        "vault service": "\n  vault:\n",
        "vault dev bootstrap service": "\n  vault-dev-bootstrap:\n",
        "dev-only Vault config": "./deploy/vault/config/vault-dev.hcl:/vault/config/vault.hcl:ro",
        "backend vault bootstrap service": "\n  backend-vault-bootstrap:\n",
        "backend runtime bridge": "BACKEND_SECRETS_FILE=/vault/runtime/backend/runtime.env",
        "backend wrapper mount": "./deploy/vault/auth/backend/run-with-rendered-secrets.sh.example:/vault/auth/backend/run-with-rendered-secrets.sh.example:ro",
        "dev vault bootstrap mount": "./deploy/vault/auth/dev/bootstrap-dev-vault.sh:/vault/auth/dev/bootstrap-dev-vault.sh:ro",
        "postgres vault env file": "${POSTGRES_VAULT_ENV_FILE_PATH:-./deploy/vault/local/dev/runtime/postgres.env}",
        "redis vault env file": "${REDIS_VAULT_ENV_FILE_PATH:-./deploy/vault/local/dev/runtime/redis.env}",
        "keycloak vault env file": "${KEYCLOAK_VAULT_ENV_FILE_PATH:-./deploy/vault/local/dev/runtime/keycloak.env}",
        "supported Swagger UI image": "image: swaggerapi/swagger-ui:v5.32.13@sha256:8f9f47436478cd8520191148a8afb7f934826d95bead1ae00898a0aa44dcdf41",
        "supported Redis image": "image: redis:8.4.5@sha256:efe6e2625e4601cd7119c4fb48b1c04cf3071f8b1729ede1216ceee8bc99742d",
        "supported Keycloak image": "image: quay.io/keycloak/keycloak:26.7.0",
        "supported Jitsi web image": "image: ghcr.io/jitsi/web:stable-11146-1@sha256:ff81559621732d3dfc4815f261d41fd826566833016ea772f4d43a77aa88fe9a",
        "supported Jitsi Prosody image": "image: ghcr.io/jitsi/prosody:stable-11146-1@sha256:0e3d9ada40c03e6eef151348e0872dce7b4b1c16c173ff4a67afeae60aba2404",
        "supported Jitsi Jicofo image": "image: ghcr.io/jitsi/jicofo:stable-11146-1@sha256:a5da296923010dcc2daf6a02e6a183181906cb969a088ae90b97516bdeb9737f",
        "supported Jitsi JVB image": "image: ghcr.io/jitsi/jvb:stable-11146-1@sha256:6a7cec66c6a2fdd8ffd3a90101a0f8e3297aff29494f258caf1bcfbd418a17f3",
        "rootless Jitsi HTTP listener": '"${DEV_JITSI_HTTP_PORT:-8000}:8000"',
        "rootless Jitsi HTTPS listener": '"${DEV_JITSI_HTTPS_PORT:-8443}:8443"',
        "rootless Jitsi web healthcheck": '"http://localhost:8000/config.js"',
        "rootless Jitsi Prosody healthcheck": '"http://127.0.0.1:5280/health"',
        "JVB host-port override": '"${DEV_JVB_UDP_PORT:-10000}:10000/udp"',
        "Jitsi web config volume": "jitsi-web-config:/config",
        "Jitsi web storage volume": "jitsi-web-storage:/storage",
        "Jitsi Prosody config volume": "jitsi-prosody-config:/config",
        "Jitsi Prosody plugin volume": "jitsi-prosody-plugins:/prosody-plugins-custom",
        "Jitsi Prosody storage volume": "jitsi-prosody-storage:/var/lib/prosody",
        "Jitsi Jicofo config volume": "jitsi-jicofo-config:/config",
        "Jitsi JVB config volume": "jitsi-jvb-config:/config",
        "file-backed Jitsi web access config": "file: ./pilot/jitsi/web/custom-meet.development.conf",
        "dev-only Keycloak realm import": "./pilot/keycloak/realm/dev:/opt/keycloak/data/import:ro",
        "dev Keycloak logout allowlist": "AUTH_LOGOUT_ALLOWED_ORIGINS=${DEV_KEYCLOAK_ORIGIN:-http://localhost:8081}",
        "private-origin logout opt-in": "AUTH_LOGOUT_ALLOW_INSECURE_PRIVATE_ORIGIN=true",
        "database-backed room config validation": "APP_FEATURES_CONFIG_SETS_FROM_DB=true",
        "dev config-set encryption key delivery": "DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY=${DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY:-0123456789ABCDEF0123456789ABCDEF}",
    }

    for label, fragment in required_fragments.items():
        if fragment not in compose_text:
            fail(f"docker-compose.yml is missing the expected {label} wiring for the dev Vault flow.")

    required_dev_vault_fragments = {
        "dev config-set encryption key input": 'DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY="${DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY:-0123456789ABCDEF0123456789ABCDEF}"',
        "dev config-set encryption key field": 'APP_CONFIG_SETS_ENCRYPTION_KEY="$DEV_VAULT_APP_CONFIG_SETS_ENCRYPTION_KEY"',
    }
    for label, fragment in required_dev_vault_fragments.items():
        if fragment not in dev_vault_bootstrap_text:
            fail(f"Development Vault bootstrap is missing the expected {label}.")

    jitsi_web_access_text = jitsi_web_access_path.read_text(encoding="utf-8")
    if 'set $portal_return_url "http://$host:3000";' not in jitsi_web_access_text:
        fail("Development Jitsi web access config must return users through the private frontend service origin.")

    if compose_text.count("image: ghcr.io/jitsi/") != 4:
        fail("docker-compose.yml must define exactly the four approved GHCR Jitsi service images.")

    jitsi_rootless_contract = {
        "dropped Linux capabilities": "    cap_drop:\n      - ALL\n",
        "no-new-privileges policy": "    security_opt:\n      - no-new-privileges:true\n",
        "read-only root filesystem": "    read_only: true\n",
        "writable rootless runtime tmpfs": "      - /run:size=16M,uid=1000,gid=1000,mode=1750,exec\n",
        "non-executable temporary tmpfs": "      - /tmp:size=16M,mode=1777,noexec\n",
    }
    for label, fragment in jitsi_rootless_contract.items():
        if compose_text.count(fragment) < 4:
            fail(f"All four dev Jitsi services must keep the {label} rootless hardening contract.")

    for retired_jitsi_release in ["stable-10741", "stable-10978"]:
        if retired_jitsi_release in compose_text:
            fail(f"docker-compose.yml still contains the retired Jitsi {retired_jitsi_release} tag.")

    issuer_contract = "${DEV_PORTAL_ORIGIN:-http://localhost:3000}"
    issuer_assignments = [
        f"APP_MEETINGS_TOKEN_ISSUER={issuer_contract}",
        f"JWT_APP_ID={issuer_contract}",
        f"JWT_ACCEPTED_ISSUERS={issuer_contract}",
    ]
    expected_issuer_counts = [1, 2, 2]
    for assignment, expected_count in zip(issuer_assignments, expected_issuer_counts):
        if compose_text.count(assignment) != expected_count:
            fail(
                "docker-compose.yml must derive backend token issuance and both "
                f"Jitsi validators from one DEV_PORTAL_ORIGIN. Expected {expected_count} "
                f"occurrence(s) of '{assignment}'."
            )

    forbidden_fragments = {
        "direct backend datasource password interpolation": "SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD?Set SPRING_DATASOURCE_PASSWORD}",
        "direct Postgres password interpolation": "POSTGRES_PASSWORD=${POSTGRES_PASSWORD?Set POSTGRES_PASSWORD}",
        "direct Keycloak admin password interpolation": "KEYCLOAK_ADMIN_PASSWORD=${KEYCLOAK_ADMIN_PASSWORD?Set KEYCLOAK_ADMIN_PASSWORD}",
    }

    for label, fragment in forbidden_fragments.items():
        if fragment in compose_text:
            fail(f"docker-compose.yml still contains {label}; dev secrets should come through Vault-managed delivery artifacts instead.")

    print("validate-dev-stack-config: OK")
    print(f"validate-dev-stack-config: verified {len(users)} seeded Keycloak user(s) with stable ids")
    print("validate-dev-stack-config: verified Postgres volume mount path")
    print("validate-dev-stack-config: verified dev Vault bootstrap wiring")
    print("validate-dev-stack-config: verified one aligned rootless Jitsi stable-11146-1 release")
    print("validate-dev-stack-config: verified one portal/Jitsi token issuer contract")
    print("validate-dev-stack-config: verified frontend framework CVE inventory versions")


if __name__ == "__main__":
    main()
