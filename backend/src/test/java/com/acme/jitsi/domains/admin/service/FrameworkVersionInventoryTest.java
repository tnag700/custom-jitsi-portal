package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FrameworkVersionInventoryTest {

  @Test
  void includesRuntimeAndBuildFrameworksThatMustNotDriftOutsideCveCoverage() {
    FrameworkVersionInventory inventory = new FrameworkVersionInventory(
        "2.0.0-beta.38",
        "2.0.0-beta.38",
        "5.2.1",
        "0.7.7",
        "7.3.6",
        "5.9.3",
        "4.3.3",
        "3.2.7",
        "10.8.1");

    List<MonitoredFramework> frameworks = inventory.list();

    assertThat(frameworks)
        .extracting(MonitoredFramework::key)
        .containsExactlyInAnyOrder(
            "spring-boot",
            "spring-framework",
            "spring-security",
            "spring-retry",
            "spring-modulith",
            "springdoc",
            "qwik",
            "qwik-router",
            "qwik-ui",
            "express",
            "vite",
            "typescript",
            "tailwind",
            "vitest",
            "eslint");
    assertThat(frameworks)
        .filteredOn(component -> "runtime".equals(component.versionSource()))
        .allSatisfy(component -> assertThat(component.currentVersion()).isNotEqualTo("unknown"));
  }
}
