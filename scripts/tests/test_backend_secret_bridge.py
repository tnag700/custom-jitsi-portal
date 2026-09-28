from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SHELL = shutil.which("sh") or ("C:/Program Files/Git/bin/bash.exe" if os.name == "nt" else None)


@unittest.skipUnless(SHELL and Path(SHELL).is_file(), "POSIX shell required")
class BackendSecretBridgeTest(unittest.TestCase):
    def test_rejects_stale_runtime_role_and_rotation_contract(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            bridge = Path(directory) / "runtime.env"
            env = {**os.environ, "BACKEND_SECRETS_FILE": bridge.as_posix(), "SPRING_DATASOURCE_USERNAME": "jitsi_app"}
            for user, contract, expected in (
                ("jitsi_app", "manual-static-kv-controlled-restart", 0),
                ("jitsi", "manual-static-kv-controlled-restart", 1),
                ("jitsi_app", "controlled-restart-static-role", 1),
            ):
                with self.subTest(user=user, contract=contract):
                    bridge.write_text(f"export SPRING_DATASOURCE_USERNAME='{user}'\nexport BACKEND_DB_ROTATION_CONTRACT='{contract}'\n", encoding="utf-8")
                    result = subprocess.run([SHELL, (ROOT / "deploy/vault/auth/backend/run-with-rendered-secrets.sh.example").as_posix(), "true"], env=env, capture_output=True, text=True, timeout=10)
                    self.assertEqual(expected, result.returncode, result.stderr)

    def test_missing_static_credentials_fail_without_dynamic_fallback(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            temp = Path(directory)
            # Only the transport is stubbed; execute the real startup shell.
            (temp / "vault").write_text("#!/bin/sh\nprintf '%s\\n' \"$*\" >> \"$VAULT_TEST_CALLS\"\ncase \"$*\" in\n  unwrap*) echo test-secret-id ;;\n  write*) echo test-token ;;\n  kv*) echo test-app-value ;;\n  read*) exit 2 ;;\nesac\n", encoding="utf-8")
            (temp / "chown").write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            for name in ("vault", "chown"):
                (temp / name).chmod(0o700)
            for name in ("role", "wrapped"):
                (temp / name).write_text("test-handoff", encoding="utf-8")
            env = {**os.environ, "VAULT_ADDR": "http://test-vault", "PATH": str(temp) + os.pathsep + os.environ["PATH"], "VAULT_TEST_CALLS": (temp / "calls").as_posix(), "ROLE_ID_FILE": (temp / "role").as_posix(), "WRAPPED_SECRET_ID_FILE": (temp / "wrapped").as_posix(), "TOKEN_SINK_FILE": (temp / "token").as_posix(), "BACKEND_ENV_OUTPUT_FILE": (temp / "runtime.env").as_posix()}
            result = subprocess.run([SHELL, (ROOT / "deploy/vault/auth/backend/startup-fetch.sh.example").as_posix()], env=env, capture_output=True, text=True, timeout=10)
            self.assertNotEqual(0, result.returncode)
            calls = (temp / "calls").read_text(encoding="utf-8")
            self.assertIn("database/static-creds/backend-app", calls)
            self.assertNotIn("database/creds/", calls)
            self.assertFalse((temp / "runtime.env").exists())
            self.assertFalse((temp / "token").exists())
            self.assertEqual([], list(temp.glob(".runtime.env.*")))
            # A complete pair publishes atomically; an ordinary restart reuses
            # it without consuming the one-use handoff again.
            vault = temp / "vault"
            vault.write_text(vault.read_text(encoding="utf-8").replace("read*) exit 2", "read*) echo test-static-value"), encoding="utf-8")
            result = subprocess.run([SHELL, (ROOT / "deploy/vault/auth/backend/startup-fetch.sh.example").as_posix()], env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("manual-static-kv-controlled-restart", (temp / "runtime.env").read_text(encoding="utf-8"))
            self.assertFalse((temp / "token").exists())
            calls = (temp / "calls").read_text(encoding="utf-8")
            result = subprocess.run([SHELL, (ROOT / "deploy/vault/auth/backend/startup-fetch.sh.example").as_posix()], env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(calls, (temp / "calls").read_text(encoding="utf-8"))
