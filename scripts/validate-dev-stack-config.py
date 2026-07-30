from __future__ import annotations

import json

from _keycloak_guardrails import validate_portal_user_profile
from _python_guardrails import fail, repo_root


def main() -> None:
    root = repo_root()
    realm_path = root / "pilot/keycloak/realm/dev/jitsi-dev-realm.json"
    compose_path = root / "docker-compose.yml"

    if not realm_path.exists():
        fail(f"Keycloak realm file not found: {realm_path}")

    if not compose_path.exists():
        fail(f"Compose file not found: {compose_path}")

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
        "supported Keycloak image": "image: quay.io/keycloak/keycloak:26.7.0",
        "supported Jitsi web image": "image: jitsi/web:stable-10978",
        "supported Jitsi Prosody image": "image: jitsi/prosody:stable-10978",
        "supported Jitsi Jicofo image": "image: jitsi/jicofo:stable-10978",
        "supported Jitsi JVB image": "image: jitsi/jvb:stable-10978",
        "dev-only Keycloak realm import": "./pilot/keycloak/realm/dev:/opt/keycloak/data/import:ro",
        "dev Keycloak logout allowlist": "AUTH_LOGOUT_ALLOWED_ORIGINS=${DEV_KEYCLOAK_ORIGIN:-http://localhost:8081}",
        "private-origin logout opt-in": "AUTH_LOGOUT_ALLOW_INSECURE_PRIVATE_ORIGIN=true",
        "database-backed room config validation": "APP_FEATURES_CONFIG_SETS_FROM_DB=true",
    }

    for label, fragment in required_fragments.items():
        if fragment not in compose_text:
            fail(f"docker-compose.yml is missing the expected {label} wiring for the dev Vault flow.")

    if compose_text.count("image: jitsi/") != 4:
        fail("docker-compose.yml must define exactly the four approved Jitsi service images.")

    if "stable-10741" in compose_text:
        fail("docker-compose.yml still contains the retired Jitsi stable-10741 tag.")

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
    print("validate-dev-stack-config: verified one aligned Jitsi stable-10978 release")
    print("validate-dev-stack-config: verified one portal/Jitsi token issuer contract")
    print("validate-dev-stack-config: verified frontend framework CVE inventory versions")


if __name__ == "__main__":
    main()
