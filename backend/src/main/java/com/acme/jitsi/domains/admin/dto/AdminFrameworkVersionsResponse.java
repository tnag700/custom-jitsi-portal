package com.acme.jitsi.domains.admin.dto;

import java.time.Instant;
import java.util.List;

public record AdminFrameworkVersionsResponse(
    Instant generatedAt,
    Instant lastSuccessfulCheckAt,
    Instant cacheExpiresAt,
    String scanStatus,
    String statusMessage,
    boolean criticalUpdateRequired,
    int vulnerabilityCount,
    int criticalVulnerabilityCount,
    List<Component> components) {

  public AdminFrameworkVersionsResponse {
    components = List.copyOf(components);
  }

  public record Component(
      String key,
      String displayName,
      String ecosystem,
      String packageName,
      String currentVersion,
      String versionSource,
      String scanStatus,
      String securityStatus,
      int vulnerabilityCount,
      int criticalVulnerabilityCount,
      List<Advisory> advisories) {

    public Component {
      advisories = List.copyOf(advisories);
    }
  }

  public record Advisory(
      String id,
      List<String> aliases,
      String summary,
      String severity,
      List<String> fixedVersions,
      String advisoryUrl,
      String modifiedAt) {

    public Advisory {
      aliases = List.copyOf(aliases);
      fixedVersions = List.copyOf(fixedVersions);
    }
  }
}
