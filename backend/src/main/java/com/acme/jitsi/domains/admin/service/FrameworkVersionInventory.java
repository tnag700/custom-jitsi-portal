package com.acme.jitsi.domains.admin.service;

import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;
import org.springframework.modulith.ApplicationModule;
import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.retry.RetryContext;
import org.springframework.stereotype.Component;

@Component
public class FrameworkVersionInventory {

  private final String qwikVersion;
  private final String qwikRouterVersion;
  private final String expressVersion;
  private final String qwikUiVersion;
  private final String viteVersion;
  private final String typescriptVersion;
  private final String tailwindVersion;
  private final String vitestVersion;
  private final String eslintVersion;

  public FrameworkVersionInventory(
      @Value("${app.version-monitor.components.qwik.version:2.0.0-beta.38}")
      String qwikVersion,
      @Value("${app.version-monitor.components.qwik-router.version:2.0.0-beta.38}")
      String qwikRouterVersion,
      @Value("${app.version-monitor.components.express.version:5.2.1}")
      String expressVersion,
      @Value("${app.version-monitor.components.qwik-ui.version:0.7.7}")
      String qwikUiVersion,
      @Value("${app.version-monitor.components.vite.version:7.3.6}")
      String viteVersion,
      @Value("${app.version-monitor.components.typescript.version:5.9.3}")
      String typescriptVersion,
      @Value("${app.version-monitor.components.tailwind.version:4.3.3}")
      String tailwindVersion,
      @Value("${app.version-monitor.components.vitest.version:3.2.7}")
      String vitestVersion,
      @Value("${app.version-monitor.components.eslint.version:10.8.1}")
      String eslintVersion) {
    this.qwikVersion = qwikVersion;
    this.qwikRouterVersion = qwikRouterVersion;
    this.expressVersion = expressVersion;
    this.qwikUiVersion = qwikUiVersion;
    this.viteVersion = viteVersion;
    this.typescriptVersion = typescriptVersion;
    this.tailwindVersion = tailwindVersion;
    this.vitestVersion = vitestVersion;
    this.eslintVersion = eslintVersion;
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
        runtimeFramework(
            "spring-retry",
            "Spring Retry",
            "org.springframework.retry:spring-retry",
            RetryContext.class.getPackage().getImplementationVersion()),
        runtimeFramework(
            "spring-modulith",
            "Spring Modulith",
            "org.springframework.modulith:spring-modulith-core",
            ApplicationModule.class.getPackage().getImplementationVersion()),
        runtimeFramework(
            "springdoc",
            "springdoc-openapi",
            "org.springdoc:springdoc-openapi-starter-webmvc-api",
            GroupedOpenApi.class.getPackage().getImplementationVersion()),
        configuredFramework("qwik", "Qwik", "@qwik.dev/core", qwikVersion),
        configuredFramework("qwik-router", "Qwik Router", "@qwik.dev/router", qwikRouterVersion),
        configuredFramework("qwik-ui", "Qwik UI", "@qwik-ui/headless", qwikUiVersion),
        configuredFramework("express", "Express", "express", expressVersion),
        configuredFramework("vite", "Vite", "vite", viteVersion),
        configuredFramework("typescript", "TypeScript", "typescript", typescriptVersion),
        configuredFramework("tailwind", "Tailwind CSS", "tailwindcss", tailwindVersion),
        configuredFramework("vitest", "Vitest", "vitest", vitestVersion),
        configuredFramework("eslint", "ESLint", "eslint", eslintVersion));
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
