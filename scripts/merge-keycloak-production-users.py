from __future__ import annotations

import argparse
import json
import os
import stat
import sys
from pathlib import Path
from typing import Any


class RealmMigrationError(RuntimeError):
    pass


def _read_json(path: Path, label: str) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise RealmMigrationError(f"{label} is missing or invalid JSON.") from exc
    if not isinstance(payload, dict):
        raise RealmMigrationError(f"{label} must contain a JSON object.")
    return payload


def merge_users(production_template: Path, development_export: Path, output: Path) -> int:
    production = _read_json(production_template, "Production realm template")
    development = _read_json(development_export, "Development realm export")

    if production.get("realm") != "jitsi":
        raise RealmMigrationError("Production realm template must target realm jitsi.")
    if development.get("realm") != "jitsi-dev":
        raise RealmMigrationError("Source realm export must identify realm jitsi-dev.")
    if production.get("users"):
        raise RealmMigrationError("Production realm template must not already contain users.")
    if output.exists():
        raise RealmMigrationError("Output realm file already exists; refusing to overwrite it.")

    users = development.get("users")
    if not isinstance(users, list) or not users:
        raise RealmMigrationError("Development realm export contains no users to migrate.")

    approved_roles = {
        role.get("name")
        for role in production.get("roles", {}).get("realm", [])
        if isinstance(role, dict) and isinstance(role.get("name"), str)
    }
    migrated_users: list[dict[str, Any]] = []
    usernames: set[str] = set()
    has_admin = False

    for raw_user in users:
        if not isinstance(raw_user, dict):
            raise RealmMigrationError("Development realm export contains an invalid user entry.")
        user = dict(raw_user)
        username = user.get("username")
        if not isinstance(username, str) or not username.strip() or username in usernames:
            raise RealmMigrationError("Development realm users must have unique non-empty usernames.")
        usernames.add(username)

        raw_roles = user.get("realmRoles", [])
        if not isinstance(raw_roles, list):
            raise RealmMigrationError("Development realm user has an invalid realmRoles field.")
        roles = sorted({role for role in raw_roles if role in approved_roles})
        user["realmRoles"] = roles
        user.pop("federationLink", None)
        user.pop("origin", None)
        if user.get("enabled", True) and "admin" in roles:
            has_admin = True
        migrated_users.append(user)

    if not has_admin:
        raise RealmMigrationError("Migrated realm must contain at least one enabled portal admin.")

    production["users"] = migrated_users
    output.parent.mkdir(parents=True, exist_ok=True)
    if os.name == "posix":
        os.chmod(output.parent, 0o700)
    temporary = output.with_name(f".{output.name}.tmp")
    temporary.write_text(json.dumps(production, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    if os.name == "posix":
        os.chmod(temporary, stat.S_IRUSR | stat.S_IWUSR)
    temporary.replace(output)
    return len(migrated_users)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(allow_abbrev=False)
    parser.add_argument("--production-template", required=True, type=Path)
    parser.add_argument("--development-export", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        count = merge_users(args.production_template, args.development_export, args.output)
    except RealmMigrationError as exc:
        print(f"merge-keycloak-production-users: FAILED: {exc}", file=sys.stderr)
        return 1
    print(f"merge-keycloak-production-users: migrated {count} user record(s) without changing the production client policy")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
