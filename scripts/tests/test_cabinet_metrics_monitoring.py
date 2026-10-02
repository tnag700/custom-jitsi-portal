from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
from _python_guardrails import get_list_section_items, get_service_block  # noqa: E402


class CabinetMetricsMonitoringTest(unittest.TestCase):
    def exporter(self, filename: str) -> str:
        text = (ROOT / filename).read_text(encoding="utf-8")
        self.assertTrue("  node-exporter:" in text, "Host collector is missing")
        return get_service_block(text, "node-exporter")

    def test_exporter_has_no_host_port_socket_or_privilege(self):
        block = self.exporter("docker-compose.production.monitoring.yml")
        self.assertEqual(get_list_section_items(block, "ports"), [])
        self.assertEqual(get_list_section_items(block, "networks"), ["ops_net"])
        self.assertIn('user: "65534:65534"', block)
        self.assertIn("read_only: true", block)
        self.assertIn("no-new-privileges:true", block)
        self.assertIn("- ALL", block)
        for forbidden in ("docker.sock", "privileged:", "network_mode:", "pid:", "cap_add:"):
            self.assertNotIn(forbidden, block)

    def test_only_host_collectors_enabled(self):
        for filename in ("docker-compose.production.monitoring.yml", "docker-compose.monitoring.yml"):
            block = self.exporter(filename)
            for flag in ("--collector.disable-defaults", "--collector.cpu", "--collector.meminfo",
                         "--collector.filesystem", "--path.procfs=/host/proc", "--path.rootfs=/host/root",
                         "--collector.filesystem.mount-points-include=^/$"):
                self.assertIn(flag, block)
            self.assertIn("read_only: true", block)
            self.assertIn("source: /etc/ssl/certs", block)
            self.assertNotIn("source: /\n", block)
            self.assertIn("create_host_path: false", block)
            self.assertNotIn("source: /sys", block)
            self.assertRegex(block, r"node-exporter:v\d+\.\d+\.\d+@sha256:[a-f0-9]{64}")

    def test_prometheus_has_both_scrape_jobs(self):
        config = (ROOT / "pilot/monitoring/prometheus/prometheus.yml").read_text(encoding="utf-8")
        self.assertIn("job_name: jitsi-backend", config)
        self.assertIn("job_name: jitsi-node", config)
        self.assertIn("node-exporter:9100", config)
        for filename in ("docker-compose.production.monitoring.yml", "docker-compose.monitoring.yml"):
            backend = get_service_block((ROOT / filename).read_text(encoding="utf-8"), "backend")
            self.assertIn("APP_METRICS_PROMETHEUS_BASE_URL=http://prometheus:9090", backend)


if __name__ == "__main__":
    unittest.main()
