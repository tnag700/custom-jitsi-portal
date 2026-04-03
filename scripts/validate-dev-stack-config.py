from __future__ import annotations

import json

from _python_guardrails import fail, repo_root


def main() -> None:
    root = repo_root()
    realm_path = root / "pilot/keycloak/realm/jitsi-dev-realm.json"
    compose_path = root / "docker-compose.yml"

    if not realm_path.exists():
        fail(f"Keycloak realm file not found: {realm_path}")

    if not compose_path.exists():
        fail(f"Compose file not found: {compose_path}")

    realm = json.loads(realm_path.read_text(encoding="utf-8"))
    users = list(realm.get("users") or [])

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
    if "- pgdata:/var/lib/postgresql\n" not in compose_text and "- pgdata:/var/lib/postgresql\r\n" not in compose_text:
        fail("docker-compose.yml must mount pgdata to /var/lib/postgresql for Postgres 18 image layout compatibility and data preservation across recreates.")

    if "- pgdata:/var/lib/postgresql/data" in compose_text:
        fail("docker-compose.yml still contains legacy pgdata mount to /var/lib/postgresql/data. Use /var/lib/postgresql with Postgres 18 images.")

    required_fragments = {
        "vault service": "\n  vault:\n",
        "vault dev bootstrap service": "\n  vault-dev-bootstrap:\n",
        "backend vault bootstrap service": "\n  backend-vault-bootstrap:\n",
        "backend runtime bridge": "BACKEND_SECRETS_FILE=/vault/runtime/backend/runtime.env",
        "backend wrapper mount": "./deploy/vault/auth/backend/run-with-rendered-secrets.sh.example:/vault/auth/backend/run-with-rendered-secrets.sh.example:ro",
        "dev vault bootstrap mount": "./deploy/vault/auth/dev/bootstrap-dev-vault.sh:/vault/auth/dev/bootstrap-dev-vault.sh:ro",
        "postgres vault env file": "${POSTGRES_VAULT_ENV_FILE_PATH:-./deploy/vault/local/dev/runtime/postgres.env}",
        "redis vault env file": "${REDIS_VAULT_ENV_FILE_PATH:-./deploy/vault/local/dev/runtime/redis.env}",
        "keycloak vault env file": "${KEYCLOAK_VAULT_ENV_FILE_PATH:-./deploy/vault/local/dev/runtime/keycloak.env}",
    }

    for label, fragment in required_fragments.items():
        if fragment not in compose_text:
            fail(f"docker-compose.yml is missing the expected {label} wiring for the dev Vault flow.")

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


if __name__ == "__main__":
    main()
