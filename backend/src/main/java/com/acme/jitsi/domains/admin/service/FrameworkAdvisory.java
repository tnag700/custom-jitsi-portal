package com.acme.jitsi.domains.admin.service;

import java.util.List;

public record FrameworkAdvisory(
    String id,
    List<String> aliases,
    String summary,
    String severity,
    List<String> fixedVersions,
    String advisoryUrl,
    String modifiedAt) {

  public FrameworkAdvisory {
    aliases = List.copyOf(aliases);
    fixedVersions = List.copyOf(fixedVersions);
  }

  public boolean isCritical() {
    return "critical".equals(severity);
  }
}
