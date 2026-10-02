package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("container")
@Testcontainers(disabledWithoutDocker = true)
class MetricCatalogPrometheusIntegrationTest {
  @Container
  @SuppressWarnings("resource")
  private static final GenericContainer<?> PROMETHEUS = new GenericContainer<>(
      "prom/prometheus:v3.15.0@sha256:efd719c99d83b060d9daefdcf00360461adf279f45ef5391f8d111892118753e")
      .withNetworkMode("none")
      .withCreateContainerCmdModifier(command -> command.withEntrypoint("/bin/sh"))
      .withCommand("-c", "sleep 3600");

  @Test
  void requiredFamiliesKeepTheirOldestRealTimestampAndRejectMissingOrDownSources() throws Exception {
    var version = PROMETHEUS.execInContainer("/bin/promtool", "--version");
    assertThat(version.getExitCode()).isZero();
    assertThat(version.getStdout() + version.getStderr()).contains("3.15.0");
    var catalog = new MetricCatalog();
    var yaml = new StringBuilder("rule_files: []\nevaluation_interval: 15s\ntests:\n");
    for (var fixture : List.of(
        List.of("host.memory", "jitsi-node", "node_memory_MemAvailable_bytes", "node_memory_MemTotal_bytes", ""),
        List.of("host.disk", "jitsi-node", "node_filesystem_avail_bytes", "node_filesystem_size_bytes", ",mountpoint=\"/\""),
        List.of("jdbc.pool", "jitsi-backend", "hikaricp_connections_active", "hikaricp_connections_max", ",pool=\"test\""),
        List.of("jwt.error-ratio", "jitsi-backend", "jitsi_join_failure_total", "jitsi_join_attempts_total", ""))) {
      String query = catalog.require(fixture.get(0)).freshnessQuery();
      for (String scenario : List.of("oldest", "missing", "down")) {
        String labels = "{job=\"" + fixture.get(1) + "\",instance=\"test\"" + fixture.get(4) + "}";
        yaml.append("  - name: '").append(fixture.get(0)).append(" ").append(scenario).append("'\n")
            .append("    interval: 15s\n    input_series:\n")
            .append("      - series: '").append(fixture.get(2)).append(labels).append("'\n")
            .append("        values: '1 1 1 1'\n");
        if (!"missing".equals(scenario)) {
          yaml.append("      - series: '").append(fixture.get(3)).append(labels).append("'\n")
              .append("        values: '1 1 1 _'\n");
        }
        yaml.append("      - series: 'up{job=\"").append(fixture.get(1)).append("\",instance=\"test\"}'\n")
            .append("        values: '").append("down".equals(scenario) ? "0 0 0 0" : "1 1 1 1").append("'\n")
            .append("    promql_expr_test:\n      - expr: '").append(query.replace("'", "''")).append("'\n")
            .append("        eval_time: 45s\n");
        yaml.append("oldest".equals(scenario)
            ? "        exp_samples:\n          - labels: '{}'\n            value: 30\n"
            : "        exp_samples: []\n");
      }
    }
    PROMETHEUS.copyFileToContainer(Transferable.of(yaml.toString().getBytes(StandardCharsets.UTF_8), 0444), "/tmp/catalog-freshness.yml");
    var run = PROMETHEUS.execInContainer("/bin/promtool", "test", "rules", "/tmp/catalog-freshness.yml");
    assertThat(run.getExitCode()).withFailMessage("promtool: %s%s", run.getStdout(), run.getStderr()).isZero();
  }
}
