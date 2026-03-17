package com.acme.jitsi.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MonitoringAlertingArtifactsContractTest {

  private static final Path REPOSITORY_ROOT = Path.of("..").normalize();

  @Test
  void phaseOneMonitoringSliceIncludesCanonicalAlertingArtifacts() throws IOException {
    Path alertRules = REPOSITORY_ROOT.resolve("pilot/monitoring/prometheus/alert-rules.yml");
    Path alertmanagerTemplate = REPOSITORY_ROOT.resolve("pilot/monitoring/alertmanager/alertmanager.yml.template");
    Path prometheusConfig = REPOSITORY_ROOT.resolve("pilot/monitoring/prometheus/prometheus.yml");
    Path monitoringCompose = REPOSITORY_ROOT.resolve("docker-compose.monitoring.yml");

    assertThat(alertRules).exists();
    assertThat(alertmanagerTemplate).exists();

    String alertRulesText = Files.readString(alertRules);
    assertThat(alertRulesText)
        .contains("JitsiJoinSuccessRateLow")
        .contains("JitsiJoinLatencyP95High")
        .contains("JitsiAuthRefreshReuseSpike")
        .contains("JitsiBackendUnavailable")
        .contains("JitsiConfigCompatibilityBroken")
        .contains("JitsiJoinReadinessBlocked")
      .contains("__MONITORING_GRAFANA_BASE_URL__")
        .contains("dashboard:")
        .contains("runbook:")
        .contains("sli_window:")
        .doesNotContain("traceId")
        .doesNotContain("subjectId")
        .doesNotContain("meetingId")
        .doesNotContain("roomId")
      .doesNotContain("ip_address")
      .doesNotContain("dashboard: http://localhost:3001");

    assertThat(Files.readString(alertmanagerTemplate))
        .contains("send_resolved: true")
        .contains("group_by:")
        .contains("__ALERTMANAGER_WEBHOOK_URL__");

    assertThat(Files.readString(prometheusConfig))
        .contains("rule_files:")
      .contains("alertmanagers:")
      .contains("__MONITORING_ENVIRONMENT__")
      .contains("__MONITORING_SERVICE_NAME__")
      .doesNotContain("environment: local")
      .doesNotContain("service: jitsi-backend");

    assertThat(Files.readString(monitoringCompose))
        .contains("alertmanager:")
      .contains("mock-alert-receiver:")
      .contains("MONITORING_ENVIRONMENT")
      .contains("MONITORING_GRAFANA_BASE_URL");
  }

  @Test
  void liveDrillAndRootScriptsCoverAlertLifecycleSmoke() throws IOException {
    String liveDrill = Files.readString(REPOSITORY_ROOT.resolve("scripts/run-observability-live-drill.ps1"));
    String packageJson = Files.readString(REPOSITORY_ROOT.resolve("package.json"));

    assertThat(liveDrill)
        .contains("AlertmanagerUrl")
        .contains("AlertReceiverUrl")
        .contains("WaitForAlertState")
        .contains("resolved");

    assertThat(packageJson)
        .contains("observability:alerting:validate")
        .contains("observability:alerting:smoke");
  }

  @Test
  void backendSourceDoesNotIntroduceProductAlertEndpoints() throws IOException {
    try (Stream<Path> sourceFiles = Files.walk(REPOSITORY_ROOT.resolve("backend/src/main/java"))) {
      String allSource = sourceFiles
          .filter(Files::isRegularFile)
          .filter(path -> path.toString().endsWith(".java"))
          .sorted(Comparator.naturalOrder())
          .map(this::readUnchecked)
          .reduce("", String::concat);

      assertThat(allSource)
          .doesNotContain("/api/v1/alerts")
          .doesNotContain("/api/v1/metrics");
    }
  }

  private String readUnchecked(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to read source file: " + path, exception);
    }
  }
}