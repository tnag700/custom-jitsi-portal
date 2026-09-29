package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminFrameworkVersionsResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FrameworkVersionMonitorService {

  private final FrameworkVersionInventory inventory;
  private final FrameworkVulnerabilityPort vulnerabilityPort;
  private final FrameworkReleasePort releasePort;
  private final Clock clock;
  private final Duration cacheTtl;
  private final boolean enabled;
  private final AtomicReference<AdminFrameworkVersionsResponse> snapshot = new AtomicReference<>();
  private final ReentrantLock refreshLock = new ReentrantLock();

  public FrameworkVersionMonitorService(
      FrameworkVersionInventory inventory,
      FrameworkVulnerabilityPort vulnerabilityPort,
      FrameworkReleasePort releasePort,
      Clock clock,
      @Value("${app.version-monitor.cache-ttl:PT6H}") Duration cacheTtl,
      @Value("${app.version-monitor.enabled:true}") boolean enabled) {
    if (cacheTtl.isNegative() || cacheTtl.isZero()) {
      throw new IllegalArgumentException("app.version-monitor.cache-ttl must be positive");
    }
    this.inventory = inventory;
    this.vulnerabilityPort = vulnerabilityPort;
    this.releasePort = releasePort;
    this.clock = clock;
    this.cacheTtl = cacheTtl;
    this.enabled = enabled;
  }

  public AdminFrameworkVersionsResponse getCurrent() {
    AdminFrameworkVersionsResponse current = snapshot.get();
    if (current == null || current.cacheExpiresAt().isBefore(clock.instant())) {
      return refresh();
    }
    return current;
  }

  public AdminFrameworkVersionsResponse refresh() {
    if (!refreshLock.tryLock()) {
      AdminFrameworkVersionsResponse current = snapshot.get();
      return current == null ? unavailableSnapshot("Проверка уже выполняется.") : current;
    }

    try {
      if (!enabled) {
        AdminFrameworkVersionsResponse disabled = disabledSnapshot();
        snapshot.set(disabled);
        return disabled;
      }

      List<MonitoredFramework> frameworks = inventory.list();
      FrameworkVulnerabilityScan vulnerabilityScan;
      Map<String, String> releases;
      try {
        vulnerabilityScan = vulnerabilityPort.scan(frameworks);
      } catch (RuntimeException exception) {
        vulnerabilityScan = new FrameworkVulnerabilityScan(Map.of());
      }
      try {
        releases = releasePort.latestVersions(frameworks);
      } catch (RuntimeException exception) {
        releases = Map.of();
      }
      AdminFrameworkVersionsResponse refreshed = buildSnapshot(
          frameworks,
          vulnerabilityScan,
          releases,
          snapshot.get());
      snapshot.set(refreshed);
      return refreshed;
    } catch (RuntimeException exception) {
      AdminFrameworkVersionsResponse current = snapshot.get();
      AdminFrameworkVersionsResponse unavailable = current == null
          ? unavailableSnapshot("Сервис проверки уязвимостей временно недоступен.")
          : staleSnapshot(current);
      snapshot.set(unavailable);
      return unavailable;
    } finally {
      refreshLock.unlock();
    }
  }

  private AdminFrameworkVersionsResponse buildSnapshot(
      List<MonitoredFramework> frameworks,
      FrameworkVulnerabilityScan scan,
      Map<String, String> releases,
      AdminFrameworkVersionsResponse previous) {
    Instant now = clock.instant();
    Map<String, AdminFrameworkVersionsResponse.Component> previousComponents =
        indexPreviousComponents(previous);
    List<AdminFrameworkVersionsResponse.Component> components = new ArrayList<>();
    int availableCount = 0;
    int completeCount = 0;

    for (MonitoredFramework framework : frameworks) {
      AdminFrameworkVersionsResponse.Component previousComponent =
          previousComponents.get(framework.key());
      ReleaseCheck release = resolveReleaseCheck(
          framework, releases.get(framework.key()), previousComponent);
      FrameworkVulnerabilityScan.ComponentScan componentScan =
          scan.components().get(framework.key());
      if (componentScan != null && componentScan.available()) {
        availableCount++;
        if (componentScan.complete()) {
          completeCount++;
        }
        components.add(toComponent(
            framework,
            componentScan.advisories(),
            componentScan.complete() ? "current" : "partial",
            release.latestVersion(),
            release.status()));
      } else {
        if (previousComponent == null) {
          components.add(toComponent(framework, List.of(), "unavailable",
              release.latestVersion(), release.status()));
        } else {
          components.add(copyAsStale(framework, previousComponent,
              release.latestVersion(), release.status()));
        }
      }
    }

    components.sort(Comparator.comparing(AdminFrameworkVersionsResponse.Component::displayName));
    String scanStatus = resolveScanStatus(frameworks.size(), availableCount, completeCount, previous);
    Instant lastSuccessfulCheckAt = "current".equals(scanStatus)
        ? now
        : previous == null ? null : previous.lastSuccessfulCheckAt();
    return response(
        now,
        lastSuccessfulCheckAt,
        scanStatus,
        statusMessage(scanStatus),
        components);
  }

  private ReleaseCheck resolveReleaseCheck(
      MonitoredFramework framework,
      String latestVersion,
      AdminFrameworkVersionsResponse.Component previous) {
    if (latestVersion == null) {
      String previousVersion = previous == null ? null : previous.latestVersion();
      return new ReleaseCheck(previousVersion,
          previousVersion == null ? "unavailable" : "stale");
    }
    Integer order = FrameworkReleaseVersions.compare(
        latestVersion, framework.currentVersion());
    if (order == null || order < 0) {
      return new ReleaseCheck(null, "unavailable");
    }
    return new ReleaseCheck(latestVersion, order > 0 ? "update_available" : "current");
  }

  private record ReleaseCheck(String latestVersion, String status) {
  }

  private Map<String, AdminFrameworkVersionsResponse.Component> indexPreviousComponents(
      AdminFrameworkVersionsResponse previous) {
    Map<String, AdminFrameworkVersionsResponse.Component> indexed = new HashMap<>();
    if (previous != null) {
      for (AdminFrameworkVersionsResponse.Component component : previous.components()) {
        indexed.put(component.key(), component);
      }
    }
    return indexed;
  }

  private String resolveScanStatus(
      int componentCount,
      int availableCount,
      int completeCount,
      AdminFrameworkVersionsResponse previous) {
    if (availableCount == componentCount && completeCount == componentCount) {
      return "current";
    }
    if (availableCount > 0) {
      return "partial";
    }
    return previous == null ? "unavailable" : "stale";
  }

  private AdminFrameworkVersionsResponse.Component toComponent(
      MonitoredFramework framework,
      List<FrameworkAdvisory> advisories,
      String scanStatus,
      String latestVersion,
      String releaseStatus) {
    List<AdminFrameworkVersionsResponse.Advisory> responseAdvisories = advisories.stream()
        .sorted(Comparator
            .comparing(FrameworkAdvisory::isCritical)
            .reversed()
            .thenComparing(FrameworkAdvisory::id))
        .map(this::toAdvisory)
        .toList();
    int criticalCount = (int) advisories.stream().filter(FrameworkAdvisory::isCritical).count();
    String securityStatus = "unavailable".equals(scanStatus) || "disabled".equals(scanStatus)
        ? "unknown"
        : criticalCount > 0 ? "critical" : advisories.isEmpty() ? "safe" : "attention";
    return new AdminFrameworkVersionsResponse.Component(
        framework.key(),
        framework.displayName(),
        framework.ecosystem(),
        framework.packageName(),
        framework.currentVersion(),
        framework.versionSource(),
        scanStatus,
        securityStatus,
        latestVersion,
        releaseStatus,
        advisories.size(),
        criticalCount,
        responseAdvisories);
  }

  private AdminFrameworkVersionsResponse.Component copyAsStale(
      MonitoredFramework framework,
      AdminFrameworkVersionsResponse.Component previous,
      String latestVersion,
      String releaseStatus) {
    return new AdminFrameworkVersionsResponse.Component(
        framework.key(),
        framework.displayName(),
        framework.ecosystem(),
        framework.packageName(),
        framework.currentVersion(),
        framework.versionSource(),
        "stale",
        previous.securityStatus(),
        latestVersion,
        releaseStatus,
        previous.vulnerabilityCount(),
        previous.criticalVulnerabilityCount(),
        previous.advisories());
  }

  private AdminFrameworkVersionsResponse.Advisory toAdvisory(FrameworkAdvisory advisory) {
    return new AdminFrameworkVersionsResponse.Advisory(
        advisory.id(),
        advisory.aliases(),
        advisory.summary(),
        advisory.severity(),
        advisory.fixedVersions(),
        advisory.advisoryUrl(),
        advisory.modifiedAt());
  }

  private AdminFrameworkVersionsResponse response(
      Instant now,
      Instant lastSuccessfulCheckAt,
      String scanStatus,
      String message,
      List<AdminFrameworkVersionsResponse.Component> components) {
    int vulnerabilityCount = components.stream()
        .mapToInt(AdminFrameworkVersionsResponse.Component::vulnerabilityCount)
        .sum();
    int criticalCount = components.stream()
        .mapToInt(AdminFrameworkVersionsResponse.Component::criticalVulnerabilityCount)
        .sum();
    int updateAvailableCount = (int) components.stream()
        .filter(component -> component.latestVersion() != null)
        .filter(component -> Integer.valueOf(1).equals(FrameworkReleaseVersions.compare(
            component.latestVersion(), component.currentVersion())))
        .count();
    return new AdminFrameworkVersionsResponse(
        now,
        lastSuccessfulCheckAt,
        now.plus(cacheTtl),
        scanStatus,
        message,
        criticalCount > 0,
        vulnerabilityCount,
        criticalCount,
        updateAvailableCount,
        components);
  }

  private AdminFrameworkVersionsResponse unavailableSnapshot(String message) {
    Instant now = clock.instant();
    List<AdminFrameworkVersionsResponse.Component> components = inventory.list().stream()
        .map(framework -> toComponent(framework, List.of(), "unavailable", null,
            "unavailable"))
        .toList();
    return response(now, null, "unavailable", message, components);
  }

  private AdminFrameworkVersionsResponse staleSnapshot(
      AdminFrameworkVersionsResponse current) {
    Instant now = clock.instant();
    List<AdminFrameworkVersionsResponse.Component> components = current.components().stream()
        .map(component -> new AdminFrameworkVersionsResponse.Component(
            component.key(),
            component.displayName(),
            component.ecosystem(),
            component.packageName(),
            component.currentVersion(),
            component.versionSource(),
            "stale",
            component.securityStatus(),
            component.latestVersion(),
            component.latestVersion() == null ? "unavailable" : "stale",
            component.vulnerabilityCount(),
            component.criticalVulnerabilityCount(),
            component.advisories()))
        .toList();
    return response(
        now,
        current.lastSuccessfulCheckAt(),
        "stale",
        statusMessage("stale"),
        components);
  }

  private AdminFrameworkVersionsResponse disabledSnapshot() {
    Instant now = clock.instant();
    List<AdminFrameworkVersionsResponse.Component> components = inventory.list().stream()
        .map(framework -> toComponent(framework, List.of(), "disabled", null, "disabled"))
        .toList();
    return response(
        now,
        null,
        "disabled",
        "Автоматическая проверка уязвимостей отключена.",
        components);
  }

  private String statusMessage(String scanStatus) {
    return switch (scanStatus) {
      case "current" -> "Версии сверены с актуальной базой известных уязвимостей.";
      case "partial" -> "Часть компонентов не удалось проверить полностью.";
      case "stale" -> "Показан последний сохранённый результат; повторная проверка не удалась.";
      default -> "Сервис проверки уязвимостей временно недоступен.";
    };
  }
}
