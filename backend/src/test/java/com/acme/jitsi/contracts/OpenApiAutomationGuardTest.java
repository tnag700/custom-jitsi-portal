package com.acme.jitsi.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OpenApiAutomationGuardTest {

  private static final Path REPO_ROOT = Path.of("..").normalize();
  private static final Path BACKEND_BUILD = REPO_ROOT.resolve("backend/build.gradle");
  private static final Path ROOT_PACKAGE = REPO_ROOT.resolve("package.json");
  private static final Path FRONTEND_PACKAGE = REPO_ROOT.resolve("frontend-qwik/package.json");
    private static final Path OPENAPI_GENERATED_BASELINE = REPO_ROOT.resolve("openapi.generated.json");
  private static final Path SECURITY_CONFIG =
      REPO_ROOT.resolve("backend/src/main/java/com/acme/jitsi/security/SecurityConfig.java");

  @Test
  void backendUsesSpringBoot403Baseline() throws IOException {
    String buildGradle = Files.readString(BACKEND_BUILD);

    assertTrue(
        buildGradle.contains("id 'org.springframework.boot' version '4.0.3'"),
        "backend/build.gradle must upgrade Spring Boot to 4.0.3");
  }

  @Test
  void repoDefinesOpenApiGenerationAndContractGateScripts() throws IOException {
    String packageJson = Files.readString(ROOT_PACKAGE);

    assertTrue(packageJson.contains("\"openapi:generate\""),
        "package.json must define an openapi:generate script");
    assertTrue(packageJson.contains("\"openapi:check\""),
        "package.json must define an openapi:check contract gate script");
  }

  @Test
    void frontendGenerationRefreshesGeneratedBaselineBeforeTypeEmission() throws IOException {
    String frontendPackageJson = Files.readString(FRONTEND_PACKAGE);

    assertTrue(
        frontendPackageJson.contains("npm run --prefix .. openapi:generate")
            || frontendPackageJson.contains("npm --prefix .. run openapi:generate"),
        "frontend-qwik/package.json must refresh generated OpenAPI baseline before generating types");
  }

  @Test
    void committedGeneratedOpenApiBaselineCarriesGeneratedMarker() throws IOException {
        String openApi = Files.readString(OPENAPI_GENERATED_BASELINE);

    assertTrue(
        openApi.contains("Generated from backend runtime contract")
            || openApi.contains("generated from backend runtime contract"),
                "openapi.generated.json must be a tracked generated baseline with an explicit marker");
  }

  @Test
  void securityAllowsOpenApiEndpoints() throws IOException {
    String securityConfig = Files.readString(SECURITY_CONFIG);

    assertTrue(
        securityConfig.contains("\"/v3/api-docs\"")
            && securityConfig.contains("\"/v3/api-docs.yaml\""),
        "SecurityConfig must expose /v3/api-docs and /v3/api-docs.yaml for generation and review workflows");
  }
}