from __future__ import annotations

import contextlib
import importlib.util
import io
import sys
import unittest
from pathlib import Path
from unittest import mock


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "validate-production-host-baseline.py"
REPO_ROOT = SCRIPT_PATH.parents[1]
sys.path.insert(0, str(SCRIPT_PATH.parent))
SPEC = importlib.util.spec_from_file_location("validate_production_host_baseline", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("Cannot load production host baseline validator")
VALIDATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VALIDATOR)

BASELINE_PATHS = (
    "deploy/host/README.md",
    "deploy/host/sshd/10-jitsi-production-hardening.conf.example",
    "deploy/host/ufw/ubuntu-24-production.rules.example",
    "deploy/host/operator-access-model.md",
    "deploy/host/journald/journald.conf.d/60-jitsi-host-baseline.conf.example",
    "docs/deployment-production.md",
    "docs/runbook.md",
    ".env.production.example",
    "README.md",
    "deploy/vault/break-glass-runbook.md",
)


class ProductionHostBaselineValidatorTest(unittest.TestCase):
    def setUp(self) -> None:
        self.files = {
            relative_path: (REPO_ROOT / relative_path).read_text(encoding="utf-8")
            for relative_path in BASELINE_PATHS
        }

    def test_accepts_current_http3_host_contract(self) -> None:
        self._validate()

    def test_rejects_missing_http3_ufw_preview(self) -> None:
        path = "deploy/host/ufw/ubuntu-24-production.rules.example"
        self.files[path] = self.files[path].replace(
            "ufw --dry-run allow 443/udp comment 'public http3 quic'\n",
            "",
        )

        self._assert_rejected("preview the HTTP/3 QUIC allow rule separately")

    def test_rejects_jvb_port_substituted_for_http3(self) -> None:
        path = "deploy/host/ufw/ubuntu-24-production.rules.example"
        self.files[path] = self.files[path].replace(
            "ufw allow 443/udp comment 'public http3 quic'",
            "ufw allow 10000/udp comment 'public http3 quic'",
        )

        self._assert_rejected("allow HTTP/3 QUIC independently from Jitsi media")

    def test_rejects_missing_cache_aware_http3_rollback(self) -> None:
        path = "deploy/host/README.md"
        self.files[path] = self.files[path].replace("Alt-Svc: clear", "remove-advertisement")

        self._assert_rejected("cache-aware HTTP/3 rollback")

    def test_rejects_guidance_that_enables_zero_rtt(self) -> None:
        path = ".env.production.example"
        self.files[path] += "# ssl_early_data on\n"

        self._assert_rejected("must not enable replayable HTTP/3 0-RTT")

    def _validate(self) -> None:
        def read_text(relative_path: str, _description: str | None = None) -> str:
            return self.files[relative_path]

        with mock.patch.object(VALIDATOR, "read_repo_text", side_effect=read_text):
            with contextlib.redirect_stdout(io.StringIO()):
                VALIDATOR.main()

    def _assert_rejected(self, expected_message: str) -> None:
        with self.assertRaisesRegex(RuntimeError, expected_message):
            self._validate()


if __name__ == "__main__":
    unittest.main()
