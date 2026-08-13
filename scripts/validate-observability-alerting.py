from __future__ import annotations

import os
from pathlib import Path

from _python_guardrails import (
    assert_contains,
    assert_not_contains,
    command_exists,
    ensure_path_exists,
    fail,
    read_text,
    repo_root,
    run_command,
    write_step,
)

PROMETHEUS_IMAGE = "prom/prometheus:v3.13.2@sha256:508729e0e2d18e11fd742a5a5ca70e557b940a93948c3c95fd0123a6fd538b69"
ALERTMANAGER_IMAGE = "prom/alertmanager:v0.33.1@sha256:9e082985f56f4c8c9f724e18f2288c6708f472e56a5286b8863d080434ea065d"
GRAFANA_IMAGE = "grafana/grafana:11.6.14-security-04@sha256:723f80992528efabc8fb9b0d220c28cc21b503ee970d3f775860c5464fb4d52f"


def resolve_monitoring_annotation_value(value: str | None, default_value: str, name: str) -> str:
    resolved = default_value if value is None or not value.strip() else value.strip()
    if "\r" in resolved or "\n" in resolved:
        fail(f"{name} must be a single-line value.")
    return resolved


def write_rendered_template(template_path: Path, output_path: Path, replacements: dict[str, str]) -> None:
    rendered = read_text(template_path)
    for key, value in replacements.items():
        rendered = rendered.replace(key, value)
    output_path.write_text(rendered, encoding="utf-8")


def main() -> None:
    root = repo_root()
    prometheus_dir = root / "pilot/monitoring/prometheus"
    alertmanager_dir = root / "pilot/monitoring/alertmanager"
    alert_rules_path = prometheus_dir / "alert-rules.yml"
    prometheus_config_path = prometheus_dir / "prometheus.yml"
    alertmanager_template_path = alertmanager_dir / "alertmanager.yml.template"
    compose_path = root / "docker-compose.monitoring.yml"
    production_compose_path = root / "docker-compose.production.monitoring.yml"

    write_step("Checking canonical alerting artifacts")
    ensure_path_exists(alert_rules_path, "Prometheus alert rules")
    ensure_path_exists(prometheus_config_path, "Prometheus config")
    ensure_path_exists(alertmanager_template_path, "Alertmanager template")
    ensure_path_exists(compose_path, "Monitoring compose override")
    ensure_path_exists(production_compose_path, "Production monitoring compose override")

    alert_rules = read_text(alert_rules_path)
    prometheus_config = read_text(prometheus_config_path)
    alertmanager_template = read_text(alertmanager_template_path)
    compose_config = read_text(compose_path)
    production_compose_config = read_text(production_compose_path)

    for alert_name in [
        "JitsiJoinSuccessRateLow",
        "JitsiJoinLatencyP95High",
        "JitsiJoinLatencyP99High",
        "JitsiAuthRefreshReuseSpike",
        "JitsiBackendUnavailable",
        "JitsiConfigCompatibilityBroken",
        "JitsiJoinReadinessBlocked",
        "JitsiJoinReadinessDegradedTooLong",
    ]:
        assert_contains(alert_rules, alert_name, f"Alert rules are missing {alert_name}.")

    for annotation in ["dashboard:", "runbook:", "sli_window:"]:
        assert_contains(alert_rules, annotation, f"Alert annotations are missing {annotation}.")

    for forbidden_label in ["traceId", "subjectId", "meetingId", "roomId", "ip_address"]:
        assert_not_contains(alert_rules, forbidden_label, f"Alert rules contain forbidden label {forbidden_label}.")

    assert_contains(prometheus_config, "rule_files:", "Prometheus config is missing rule_files.")
    assert_contains(prometheus_config, "alertmanagers:", "Prometheus config is missing alertmanagers.")
    assert_contains(alertmanager_template, "__ALERTMANAGER_WEBHOOK_URL__", "Alertmanager template is missing webhook placeholder.")
    assert_contains(alertmanager_template, "send_resolved: true", "Alertmanager template must send resolved notifications.")
    assert_contains(compose_config, "alertmanager:", "Monitoring compose override is missing alertmanager service.")
    assert_contains(compose_config, "mock-alert-receiver:", "Monitoring compose override is missing mock alert receiver service.")
    for label, image in {
        "Prometheus": PROMETHEUS_IMAGE,
        "Alertmanager": ALERTMANAGER_IMAGE,
        "Grafana security bridge": GRAFANA_IMAGE,
    }.items():
        for compose_label, compose_text in {
            "development": compose_config,
            "production": production_compose_config,
        }.items():
            assert_contains(
                compose_text,
                f"image: {image}",
                f"{compose_label.capitalize()} monitoring must pin the approved {label} image and manifest digest.",
            )

    if not command_exists("docker"):
        fail("Docker CLI is required to validate Prometheus and Alertmanager configuration.")

    rendered_prometheus_config_path = prometheus_dir / "prometheus.rendered.yml"
    rendered_alert_rules_path = prometheus_dir / "alert-rules.rendered.yml"
    rendered_alertmanager_path = alertmanager_dir / "alertmanager.rendered.yml"

    webhook_url = os.environ.get("ALERTMANAGER_WEBHOOK_URL", "http://mock-alert-receiver:9080/alerts")
    monitoring_environment = os.environ.get("MONITORING_ENVIRONMENT", "local")
    monitoring_service_name = os.environ.get("MONITORING_SERVICE_NAME", "jitsi-backend")
    grafana_base_url = os.environ.get("MONITORING_GRAFANA_BASE_URL", "http://localhost:3001")
    runbook_url = resolve_monitoring_annotation_value(
        os.environ.get("MONITORING_RUNBOOK_URL"),
        "docs/runbook.md#phase-1-alerting",
        "MONITORING_RUNBOOK_URL",
    )

    try:
        write_step("Running promtool check config and rules")
        write_rendered_template(
            prometheus_config_path,
            rendered_prometheus_config_path,
            {
                "__MONITORING_ENVIRONMENT__": monitoring_environment,
                "__MONITORING_SERVICE_NAME__": monitoring_service_name,
            },
        )
        write_rendered_template(
            alert_rules_path,
            rendered_alert_rules_path,
            {
                "__MONITORING_GRAFANA_BASE_URL__": grafana_base_url,
                "__MONITORING_RUNBOOK_URL__": runbook_url,
            },
        )
        promtool_config_result = run_command(
            [
                "docker",
                "run",
                "--rm",
                "--entrypoint",
                "/bin/promtool",
                "-v",
                f"{prometheus_dir.resolve()}:/etc/prometheus:ro",
                "-v",
                f"{rendered_alert_rules_path.resolve()}:/tmp/alert-rules.yml:ro",
                PROMETHEUS_IMAGE,
                "check",
                "config",
                "/etc/prometheus/prometheus.rendered.yml",
            ]
        )
        if promtool_config_result.returncode != 0:
            fail(
                "promtool check config failed.\n"
                + (promtool_config_result.stdout + promtool_config_result.stderr).strip()
            )

        promtool_result = run_command(
            [
                "docker",
                "run",
                "--rm",
                "--entrypoint",
                "/bin/promtool",
                "-v",
                f"{prometheus_dir.resolve()}:/etc/prometheus:ro",
                PROMETHEUS_IMAGE,
                "check",
                "rules",
                "/etc/prometheus/alert-rules.rendered.yml",
            ]
        )
        if promtool_result.returncode != 0:
            fail("promtool check rules failed.\n" + (promtool_result.stdout + promtool_result.stderr).strip())

        write_step("Running amtool check-config")
        write_rendered_template(
            alertmanager_template_path,
            rendered_alertmanager_path,
            {"__ALERTMANAGER_WEBHOOK_URL__": webhook_url},
        )
        amtool_result = run_command(
            [
                "docker",
                "run",
                "--rm",
                "--entrypoint",
                "/bin/amtool",
                "-v",
                f"{alertmanager_dir.resolve()}:/etc/alertmanager:ro",
                ALERTMANAGER_IMAGE,
                "check-config",
                "/etc/alertmanager/alertmanager.rendered.yml",
            ]
        )
        if amtool_result.returncode != 0:
            fail("amtool check-config failed.\n" + (amtool_result.stdout + amtool_result.stderr).strip())
    finally:
        for path in [
            rendered_prometheus_config_path,
            rendered_alert_rules_path,
            rendered_alertmanager_path,
        ]:
            if path.exists():
                path.unlink()

    write_step("Alerting configuration validated successfully")


if __name__ == "__main__":
    main()
