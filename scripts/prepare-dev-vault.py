from __future__ import annotations

import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOCAL_DEV_ROOT = ROOT / "deploy" / "vault" / "local" / "dev"
BACKEND_DIR = LOCAL_DEV_ROOT / "backend"
INIT_DIR = LOCAL_DEV_ROOT / "init"
RUNTIME_DIR = LOCAL_DEV_ROOT / "runtime"


PLACEHOLDER_ENV_FILES = {
    "postgres.env": "POSTGRES_PASSWORD=bootstrap-pending\n",
    "redis.env": "REDIS_PASSWORD=bootstrap-pending\n",
    "keycloak.env": "KEYCLOAK_ADMIN_PASSWORD=bootstrap-pending\n",
    "jitsi-web.env": (
        "JWT_APP_SECRET=bootstrap-pending\n"
        "JICOFO_AUTH_PASSWORD=bootstrap-pending\n"
        "JVB_AUTH_PASSWORD=bootstrap-pending\n"
    ),
    "jitsi-prosody.env": (
        "JWT_APP_SECRET=bootstrap-pending\n"
        "JICOFO_AUTH_PASSWORD=bootstrap-pending\n"
        "JVB_AUTH_PASSWORD=bootstrap-pending\n"
    ),
    "jitsi-jicofo.env": (
        "JICOFO_AUTH_PASSWORD=bootstrap-pending\n"
        "JICOFO_COMPONENT_SECRET=bootstrap-pending\n"
    ),
    "jitsi-jvb.env": "JVB_AUTH_PASSWORD=bootstrap-pending\n",
}


def ensure_local_dev_paths() -> None:
    BACKEND_DIR.mkdir(parents=True, exist_ok=True)
    INIT_DIR.mkdir(parents=True, exist_ok=True)
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)

    for file_name, placeholder in PLACEHOLDER_ENV_FILES.items():
        file_path = RUNTIME_DIR / file_name
        if not file_path.exists():
            file_path.write_text(placeholder, encoding="utf-8")

    for file_name in ("role_id", "wrapped-secret-id"):
        file_path = BACKEND_DIR / file_name
        if not file_path.exists():
            file_path.write_text("bootstrap-pending\n", encoding="utf-8")


def run(command: list[str]) -> None:
    completed = subprocess.run(command, cwd=ROOT, check=False)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)


def ensure_bootstrap_outputs() -> None:
    expected_files = [
        BACKEND_DIR / "role_id",
        BACKEND_DIR / "wrapped-secret-id",
        RUNTIME_DIR / "postgres.env",
        RUNTIME_DIR / "redis.env",
        RUNTIME_DIR / "keycloak.env",
        RUNTIME_DIR / "jitsi-web.env",
        RUNTIME_DIR / "jitsi-prosody.env",
        RUNTIME_DIR / "jitsi-jicofo.env",
        RUNTIME_DIR / "jitsi-jvb.env",
    ]

    for file_path in expected_files:
        if not file_path.exists():
            raise SystemExit(f"prepare-dev-vault: expected bootstrap artifact is missing: {file_path}")

        content = file_path.read_text(encoding="utf-8").strip()
        if not content or "bootstrap-pending" in content:
            raise SystemExit(f"prepare-dev-vault: bootstrap artifact was not rendered: {file_path}")


def main() -> None:
    ensure_local_dev_paths()
    run(["docker", "compose", "up", "-d", "--build", "vault"])
    run(["docker", "compose", "run", "--rm", "--no-deps", "vault-dev-bootstrap"])
    ensure_bootstrap_outputs()
    print("prepare-dev-vault: OK")
    print(f"prepare-dev-vault: rendered artifacts under {LOCAL_DEV_ROOT}")


if __name__ == "__main__":
    main()