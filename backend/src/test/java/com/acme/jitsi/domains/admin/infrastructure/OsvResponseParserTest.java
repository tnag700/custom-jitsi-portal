package com.acme.jitsi.domains.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.admin.service.FrameworkVulnerabilityScan;
import com.acme.jitsi.domains.admin.service.MonitoredFramework;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OsvResponseParserTest {

  private final OsvResponseParser parser = new OsvResponseParser(JsonMapper.builder().build());
  private final MonitoredFramework framework = new MonitoredFramework(
      "express",
      "Express",
      "npm",
      "express",
      "5.2.1",
      "build-config");

  @Test
  void parsesExplicitCriticalSeverityAndMatchingFixedVersion() {
    String response = """
        {
          "vulns": [{
            "id": "GHSA-aaaa-bbbb-cccc",
            "aliases": ["CVE-2026-12345"],
            "summary": "Critical issue",
            "modified": "2026-07-29T10:00:00Z",
            "database_specific": {"severity": "CRITICAL"},
            "references": [
              {"type": "ADVISORY", "url": "https://github.com/advisories/GHSA-aaaa-bbbb-cccc"}
            ],
            "affected": [{
              "package": {"ecosystem": "npm", "name": "express"},
              "ranges": [{
                "type": "SEMVER",
                "events": [{"introduced": "0"}, {"fixed": "5.2.2"}]
              }]
            }]
          }]
        }
        """;

    FrameworkVulnerabilityScan.ComponentScan result = parser.parse(framework, response);

    assertThat(result.available()).isTrue();
    assertThat(result.complete()).isTrue();
    assertThat(result.advisories()).singleElement().satisfies(advisory -> {
      assertThat(advisory.severity()).isEqualTo("critical");
      assertThat(advisory.aliases()).containsExactly("CVE-2026-12345");
      assertThat(advisory.fixedVersions()).containsExactly("5.2.2");
      assertThat(advisory.advisoryUrl())
          .isEqualTo("https://github.com/advisories/GHSA-aaaa-bbbb-cccc");
    });
  }

  @Test
  void rejectsUnsafeReferenceAndDoesNotInventCriticalSeverity() {
    String response = """
        {
          "vulns": [{
            "id": "GHSA-aaaa-bbbb-dddd",
            "summary": "Severity not supplied",
            "references": [
              {"type": "ADVISORY", "url": "javascript:alert(1)"}
            ],
            "severity": [{
              "type": "CVSS_V3",
              "score": "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H"
            }]
          }]
        }
        """;

    FrameworkVulnerabilityScan.ComponentScan result = parser.parse(framework, response);

    assertThat(result.advisories()).singleElement().satisfies(advisory -> {
      assertThat(advisory.severity()).isEqualTo("unknown");
      assertThat(advisory.advisoryUrl())
          .isEqualTo("https://osv.dev/vulnerability/GHSA-aaaa-bbbb-dddd");
    });
  }

  @Test
  void marksPaginatedResponseAsIncomplete() {
    FrameworkVulnerabilityScan.ComponentScan result = parser.parse(
        framework,
        """
            {
              "vulns": [],
              "next_page_token": "opaque-token"
            }
            """);

    assertThat(result.complete()).isFalse();
  }
}
