package com.acme.jitsi.domains.admin.service;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.stereotype.Component;

@Component
public class FrameworkVersionInventory {

  private final String qwikVersion;
  private final String qwikRouterVersion;
  private final String expressVersion;

  public FrameworkVersionInventory(
      @Value("${app.version-monitor.components.qwik.version:2.0.0-beta.38}")
      String qwikVersion,
      @Value("${app.version-monitor.components.qwik-router.version:2.0.0-beta.38}")
      String qwikRouterVersion,
      @Value("${app.version-monitor.components.express.version:5.2.1}")
      String expressVersion) {
    this.qwikVersion = qwikVersion;
    this.qwikRouterVersion = qwikRouterVersion;
    this.expressVersion = expressVersion;
  }

  public List<MonitoredFramework> list() {
    return List.of(
        runtimeFramework(
            "spring-boot",
            "Spring Boot",
            "org.springframework.boot:spring-boot",
            SpringBootVersion.getVersion()),
        runtimeFramework(
            "spring-framework",
            "Spring Framework",
            "org.springframework:spring-core",
            SpringVersion.getVersion()),
        runtimeFramework(
            "spring-security",
            "Spring Security",
            "org.springframework.security:spring-security-core",
            SpringSecurityCoreVersion.getVersion()),
        configuredFramework("qwik", "Qwik", "@qwik.dev/core", qwikVersion),
        configuredFramework("qwik-router", "Qwik Router", "@qwik.dev/router", qwikRouterVersion),
        configuredFramework("express", "Express", "express", expressVersion));
  }

  private MonitoredFramework runtimeFramework(
      String key,
      String displayName,
      String packageName,
      String version) {
    return new MonitoredFramework(
        key,
        displayName,
        "Maven",
        packageName,
        normalizeVersion(version),
        "runtime");
  }

  private MonitoredFramework configuredFramework(
      String key,
      String displayName,
      String packageName,
      String version) {
    return new MonitoredFramework(
        key,
        displayName,
        "npm",
        packageName,
        normalizeVersion(version),
        "build-config");
  }

  private String normalizeVersion(String version) {
    return version == null || version.isBlank() ? "unknown" : version.trim();
  }
}
