package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.FrameworkReleasePort;
import com.acme.jitsi.domains.admin.service.FrameworkReleaseVersions;
import com.acme.jitsi.domains.admin.service.MonitoredFramework;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
class FrameworkReleaseAdapter implements FrameworkReleasePort {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final int MAX_RESPONSE_BYTES = 1_048_576;
  private static final Pattern MAVEN_VERSION = Pattern.compile("<version>([^<]+)</version>");
  private static final Pattern MAVEN_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
  private static final Pattern NPM_NAME = Pattern.compile(
      "(?:@[a-z0-9][a-z0-9._-]*/)?[a-z0-9][a-z0-9._-]*");
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final HttpClient httpClient = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3))
      .followRedirects(HttpClient.Redirect.NEVER)
      .build();

  @Override
  public Map<String, String> latestVersions(List<MonitoredFramework> frameworks) {
    Map<String, CompletableFuture<String>> pending = new LinkedHashMap<>();
    for (MonitoredFramework framework : frameworks) {
      pending.put(framework.key(), query(framework));
    }
    CompletableFuture.allOf(pending.values().toArray(CompletableFuture[]::new)).join();
    Map<String, String> result = new LinkedHashMap<>();
    pending.forEach((key, future) -> {
      String version = future.join();
      if (version != null) {
        result.put(key, version);
      }
    });
    return result;
  }

  private CompletableFuture<String> query(MonitoredFramework framework) {
    try {
      URI uri = releaseUri(framework);
      HttpRequest request = HttpRequest.newBuilder(uri)
          .timeout(REQUEST_TIMEOUT)
          .header("Accept", "application/json, application/xml, text/xml")
          .GET()
          .build();
      return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
          .thenApply(response -> parseResponse(framework, response))
          .exceptionally(ignored -> null);
    } catch (RuntimeException exception) {
      return CompletableFuture.completedFuture(null);
    }
  }

  static URI releaseUri(MonitoredFramework framework) {
    if ("Maven".equals(framework.ecosystem())) {
      String[] coordinates = framework.packageName().split(":", -1);
      if (coordinates.length != 2
          || !MAVEN_NAME.matcher(coordinates[0]).matches()
          || !MAVEN_NAME.matcher(coordinates[1]).matches()) {
        throw new IllegalArgumentException("Invalid Maven coordinates");
      }
      return URI.create("https://repo.maven.apache.org/maven2/"
          + coordinates[0].replace('.', '/') + "/" + coordinates[1]
          + "/maven-metadata.xml");
    }
    if ("npm".equals(framework.ecosystem())
        && NPM_NAME.matcher(framework.packageName()).matches()) {
      return URI.create("https://registry.npmjs.org/"
          + framework.packageName().replace("/", "%2F") + "/latest");
    }
    throw new IllegalArgumentException("Unsupported release source");
  }

  private String parseResponse(
      MonitoredFramework framework,
      HttpResponse<InputStream> response) {
    if (response.statusCode() != 200) {
      closeQuietly(response.body());
      return null;
    }
    try (InputStream body = response.body()) {
      byte[] bytes = body.readNBytes(MAX_RESPONSE_BYTES + 1);
      if (bytes.length > MAX_RESPONSE_BYTES) {
        return null;
      }
      String content = new String(bytes, StandardCharsets.UTF_8);
      return "Maven".equals(framework.ecosystem())
          ? parseMavenMetadata(content) : parseNpmVersion(content);
    } catch (IOException | RuntimeException exception) {
      return null;
    }
  }

  static String parseMavenMetadata(String metadata) {
    Matcher matcher = MAVEN_VERSION.matcher(metadata);
    String latest = null;
    while (matcher.find()) {
      String version = matcher.group(1).trim();
      if (version.contains("-") || FrameworkReleaseVersions.compare(version, version) == null) {
        continue;
      }
      if (latest == null || FrameworkReleaseVersions.compare(version, latest) > 0) {
        latest = version;
      }
    }
    return latest;
  }

  static String parseNpmVersion(String document) {
    try {
      String version = JSON.readTree(document).path("version").asText();
      return FrameworkReleaseVersions.compare(version, version) == null ? null : version;
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private void closeQuietly(InputStream body) {
    try {
      body.close();
    } catch (IOException ignored) {
      // The response is discarded.
    }
  }
}
