package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.FrameworkAdvisory;
import com.acme.jitsi.domains.admin.service.FrameworkVulnerabilityScan;
import com.acme.jitsi.domains.admin.service.MonitoredFramework;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
class OsvResponseParser {

  private static final int MAX_ADVISORIES = 100;
  private static final int MAX_ALIASES = 10;
  private static final int MAX_FIXED_VERSIONS = 10;
  private static final int MAX_TEXT_LENGTH = 300;
  private final ObjectMapper objectMapper;

  OsvResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  FrameworkVulnerabilityScan.ComponentScan parse(
      MonitoredFramework framework,
      String responseBody) {
    JsonNode root = objectMapper.readTree(responseBody);
    JsonNode vulnerabilities = root.path("vulns");
    if (!vulnerabilities.isArray()) {
      return FrameworkVulnerabilityScan.ComponentScan.available(List.of(), true);
    }

    List<FrameworkAdvisory> advisories = new ArrayList<>();
    for (JsonNode vulnerability : vulnerabilities) {
      if (advisories.size() >= MAX_ADVISORIES) {
        break;
      }
      FrameworkAdvisory advisory = parseAdvisory(framework, vulnerability);
      if (advisory != null) {
        advisories.add(advisory);
      }
    }

    boolean complete = textValue(root.path("next_page_token"), "").isBlank()
        && vulnerabilities.size() <= MAX_ADVISORIES;
    return FrameworkVulnerabilityScan.ComponentScan.available(advisories, complete);
  }

  private FrameworkAdvisory parseAdvisory(
      MonitoredFramework framework,
      JsonNode vulnerability) {
    String id = safeIdentifier(textValue(vulnerability.path("id"), ""));
    if (id.isBlank()) {
      return null;
    }

    List<String> aliases = boundedTextList(vulnerability.path("aliases"), MAX_ALIASES);
    String summary = safeText(textValue(
        vulnerability.path("summary"),
        "Описание отсутствует."));
    String severity = resolveSeverity(framework, vulnerability);
    List<String> fixedVersions = fixedVersions(framework, vulnerability);
    String advisoryUrl = resolveAdvisoryUrl(id, vulnerability.path("references"));
    String modifiedAt = safeText(textValue(vulnerability.path("modified"), ""));
    return new FrameworkAdvisory(
        id,
        aliases,
        summary,
        severity,
        fixedVersions,
        advisoryUrl,
        modifiedAt);
  }

  private String resolveSeverity(
      MonitoredFramework framework,
      JsonNode vulnerability) {
    Set<String> candidates = new LinkedHashSet<>();
    addSeverity(candidates, vulnerability.path("database_specific").path("severity"));
    addSeverity(candidates, vulnerability.path("ecosystem_specific").path("severity"));

    JsonNode affected = vulnerability.path("affected");
    if (affected.isArray()) {
      for (JsonNode entry : affected) {
        if (matchesFramework(framework, entry.path("package"))) {
          addSeverity(candidates, entry.path("database_specific").path("severity"));
          addSeverity(candidates, entry.path("ecosystem_specific").path("severity"));
        }
      }
    }

    for (String severity : List.of("critical", "high", "moderate", "medium", "low")) {
      if (candidates.contains(severity)) {
        return "moderate".equals(severity) ? "medium" : severity;
      }
    }
    return "unknown";
  }

  private void addSeverity(Set<String> candidates, JsonNode node) {
    if (node.isString()) {
      String normalized = node.stringValue().trim().toLowerCase(Locale.ROOT);
      if (!normalized.isBlank()) {
        candidates.add(normalized);
      }
    }
  }

  private List<String> fixedVersions(
      MonitoredFramework framework,
      JsonNode vulnerability) {
    LinkedHashSet<String> versions = new LinkedHashSet<>();
    JsonNode affected = vulnerability.path("affected");
    if (!affected.isArray()) {
      return List.of();
    }

    for (JsonNode entry : affected) {
      if (!matchesFramework(framework, entry.path("package"))) {
        continue;
      }
      JsonNode ranges = entry.path("ranges");
      if (!ranges.isArray()) {
        continue;
      }
      for (JsonNode range : ranges) {
        JsonNode events = range.path("events");
        if (!events.isArray()) {
          continue;
        }
        for (JsonNode event : events) {
          String fixed = safeVersion(textValue(event.path("fixed"), ""));
          if (!fixed.isBlank() && versions.size() < MAX_FIXED_VERSIONS) {
            versions.add(fixed);
          }
        }
      }
    }
    return List.copyOf(versions);
  }

  private boolean matchesFramework(MonitoredFramework framework, JsonNode packageNode) {
    return framework.ecosystem().equals(textValue(packageNode.path("ecosystem"), ""))
        && framework.packageName().equals(textValue(packageNode.path("name"), ""));
  }

  private String resolveAdvisoryUrl(String id, JsonNode references) {
    if (references.isArray()) {
      for (JsonNode reference : references) {
        if (!"ADVISORY".equalsIgnoreCase(textValue(reference.path("type"), ""))) {
          continue;
        }
        String safeUrl = safeHttpsUrl(textValue(reference.path("url"), ""));
        if (!safeUrl.isBlank()) {
          return safeUrl;
        }
      }
    }
    return "https://osv.dev/vulnerability/" + id;
  }

  private String safeHttpsUrl(String rawUrl) {
    try {
      URI uri = URI.create(rawUrl);
      if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) {
        return uri.toASCIIString();
      }
    } catch (IllegalArgumentException ignored) {
      return "";
    }
    return "";
  }

  private List<String> boundedTextList(JsonNode node, int maxItems) {
    if (!node.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      String value = safeIdentifier(textValue(item, ""));
      if (!value.isBlank() && values.size() < maxItems) {
        values.add(value);
      }
    }
    return List.copyOf(values);
  }

  private String safeIdentifier(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.length() > 100 || !normalized.matches("[A-Za-z0-9._:-]+")) {
      return "";
    }
    return normalized;
  }

  private String safeVersion(String value) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.length() > 100 || !normalized.matches("[A-Za-z0-9._+:-]+")) {
      return "";
    }
    return normalized;
  }

  private String safeText(String value) {
    if (value == null) {
      return "";
    }
    String normalized = value.replaceAll("\\p{Cntrl}", " ").trim();
    return normalized.length() <= MAX_TEXT_LENGTH
        ? normalized
        : normalized.substring(0, MAX_TEXT_LENGTH);
  }

  private String textValue(JsonNode node, String fallback) {
    return node.isString() ? node.stringValue() : fallback;
  }
}
