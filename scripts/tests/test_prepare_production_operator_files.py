from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHELL = shutil.which("sh") or ("C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else None)


@unittest.skipUnless(SHELL and Path(SHELL).is_file(), "POSIX shell and OpenSSL required")
class ProductionOperatorFilesTest(unittest.TestCase):
    def test_runtime_passwords_match_consumers_and_never_equal_bootstrap(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copyfile(ROOT / ".env.production.example", root / ".env.production.example")
            command = [SHELL, (ROOT / "scripts/prepare-production-operator-files.sh").as_posix(), root.as_posix()]
            # Git Bash otherwise rewrites OpenSSL /CN= subjects as paths.
            env = {**os.environ, "MSYS_NO_PATHCONV": "1"}
            result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=45)
            self.assertEqual(0, result.returncode, result.stderr)
            operator = root / "deploy/production/local"

            def read_env(path: Path) -> dict[str, str]:
                return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines())

            backend_db = read_env(operator / "secrets/postgres.env")
            keycloak_db = read_env(operator / "secrets/keycloak-postgres.env")
            keycloak = read_env(operator / "secrets/keycloak.env")
            seed = read_env(operator / "vault/seed.env")
            self.assertNotEqual(backend_db["POSTGRES_PASSWORD"], backend_db["APP_DB_PASSWORD"])
            self.assertNotEqual(keycloak_db["POSTGRES_PASSWORD"], keycloak_db["APP_DB_PASSWORD"])
            self.assertEqual(backend_db["APP_DB_PASSWORD"], seed["APP_POSTGRES_PASSWORD"])
            self.assertEqual(keycloak_db["APP_DB_PASSWORD"], seed["KEYCLOAK_DB_PASSWORD"])
            self.assertEqual(keycloak_db["APP_DB_PASSWORD"], keycloak["KC_DB_PASSWORD"])
            self.assertNotIn(backend_db["POSTGRES_PASSWORD"], seed.values())
            self.assertNotIn(keycloak_db["POSTGRES_PASSWORD"], seed.values())
            result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual(seed, read_env(operator / "vault/seed.env"))
