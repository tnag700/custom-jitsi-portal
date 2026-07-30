package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.admin.dto.AdminFrameworkVersionsResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FrameworkVersionMonitorServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
  private static final MonitoredFramework QWIK = new MonitoredFramework(
      "qwik",
      "Qwik",
      "npm",
      "@qwik.dev/core",
      "2.0.0-beta.38",
      "build-config");

  @Test
  void criticalAdvisoryActivatesUpdateNotificationAndKeepsFixedVersions() {
    FrameworkAdvisory advisory = new FrameworkAdvisory(
        "GHSA-test-critical",
        List.of("CVE-2026-12345"),
        "Critical framework vulnerability",
        "critical",
        List.of("2.0.0"),
        "https://osv.dev/vulnerability/GHSA-test-critical",
        "2026-07-29T10:00:00Z");
    FrameworkVersionMonitorService service = service(
        frameworks -> new FrameworkVulnerabilityScan(Map.of(
            "qwik",
            FrameworkVulnerabilityScan.ComponentScan.available(List.of(advisory), true))),
        Clock.fixed(NOW, ZoneOffset.UTC));

    AdminFrameworkVersionsResponse response = service.refresh();

    assertThat(response.scanStatus()).isEqualTo("current");
    assertThat(response.criticalUpdateRequired()).isTrue();
    assertThat(response.criticalVulnerabilityCount()).isEqualTo(1);
    assertThat(response.components()).singleElement().satisfies(component -> {
      assertThat(component.securityStatus()).isEqualTo("critical");
      assertThat(component.currentVersion()).isEqualTo("2.0.0-beta.38");
      assertThat(component.advisories()).singleElement().satisfies(result ->
          assertThat(result.fixedVersions()).containsExactly("2.0.0"));
    });
  }

  @Test
  void unavailableRefreshKeepsLastKnownCriticalResultAsStale() {
    AtomicReference<FrameworkVulnerabilityScan.ComponentScan> result =
        new AtomicReference<>(FrameworkVulnerabilityScan.ComponentScan.available(
            List.of(new FrameworkAdvisory(
                "GHSA-test-critical",
                List.of("CVE-2026-12345"),
                "Critical framework vulnerability",
                "critical",
                List.of("2.0.0"),
                "https://osv.dev/vulnerability/GHSA-test-critical",
                "2026-07-29T10:00:00Z")),
            true));
    FrameworkVersionMonitorService service = service(
        frameworks -> new FrameworkVulnerabilityScan(Map.of("qwik", result.get())),
        Clock.fixed(NOW, ZoneOffset.UTC));
    service.refresh();
    result.set(FrameworkVulnerabilityScan.ComponentScan.unavailable());

    AdminFrameworkVersionsResponse response = service.refresh();

    assertThat(response.scanStatus()).isEqualTo("stale");
    assertThat(response.lastSuccessfulCheckAt()).isEqualTo(NOW);
    assertThat(response.criticalUpdateRequired()).isTrue();
    assertThat(response.components()).singleElement().satisfies(component -> {
      assertThat(component.scanStatus()).isEqualTo("stale");
      assertThat(component.criticalVulnerabilityCount()).isEqualTo(1);
    });
  }

  @Test
  void incompleteProviderPageProducesPartialStatusWithoutFalseCriticalAlert() {
    FrameworkVersionMonitorService service = service(
        frameworks -> new FrameworkVulnerabilityScan(Map.of(
            "qwik",
            FrameworkVulnerabilityScan.ComponentScan.available(
                List.of(new FrameworkAdvisory(
                    "GHSA-test-high",
                    List.of("CVE-2026-23456"),
                    "High framework vulnerability",
                    "high",
                    List.of(),
                    "https://osv.dev/vulnerability/GHSA-test-high",
                    "2026-07-29T10:00:00Z")),
                false))),
        Clock.fixed(NOW, ZoneOffset.UTC));

    AdminFrameworkVersionsResponse response = service.refresh();

    assertThat(response.scanStatus()).isEqualTo("partial");
    assertThat(response.criticalUpdateRequired()).isFalse();
    assertThat(response.vulnerabilityCount()).isEqualTo(1);
  }

  private FrameworkVersionMonitorService service(
      FrameworkVulnerabilityPort port,
      Clock clock) {
    FrameworkVersionInventory inventory = new FrameworkVersionInventory(
        QWIK.currentVersion(),
        "unknown",
        "unknown") {
      @Override
      public List<MonitoredFramework> list() {
        return List.of(QWIK);
      }
    };
    return new FrameworkVersionMonitorService(
        inventory,
        port,
        clock,
        Duration.ofHours(6),
        true);
  }
}
