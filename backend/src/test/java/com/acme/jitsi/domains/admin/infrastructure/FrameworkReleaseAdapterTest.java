package com.acme.jitsi.domains.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.admin.service.MonitoredFramework;
import org.junit.jupiter.api.Test;

class FrameworkReleaseAdapterTest {

  @Test
  void qwikUsesPublishersLatestChannelForCoreAndRouter() {
    for (String component : new String[] {"core", "router"}) {
      var framework = new MonitoredFramework(
          "core".equals(component) ? "qwik" : "qwik-router", "Qwik", "npm",
          "@qwik.dev/" + component, "2.0.0-beta.38", "package-lock");

      assertThat(FrameworkReleaseAdapter.releaseUri(framework).toString())
          .isEqualTo("https://registry.npmjs.org/@qwik.dev%2F" + component + "/latest");
    }
  }

  @Test
  void mavenMetadataPicksNewestStableReleaseRatherThanPreview() {
    String metadata = """
        <metadata><versioning><versions>
          <version>4.1.0</version><version>4.1.1</version>
          <version>4.2.0-M2</version><version>4.0.8</version>
        </versions></versioning></metadata>
        """;

    assertThat(FrameworkReleaseAdapter.parseMavenMetadata(metadata)).isEqualTo("4.1.1");
  }

  @Test
  void npmTagUsesVersionFieldAndRejectsMalformedPayload() {
    assertThat(FrameworkReleaseAdapter.parseNpmVersion("{\"version\":\"2.0.0-beta.40\"}"))
        .isEqualTo("2.0.0-beta.40");
    assertThat(FrameworkReleaseAdapter.parseNpmVersion("{\"version\":\"bad\"}"))
        .isNull();
  }
}
