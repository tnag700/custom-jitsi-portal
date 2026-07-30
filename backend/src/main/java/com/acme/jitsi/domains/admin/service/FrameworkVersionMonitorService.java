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
  private final Clock clock;
  private final Duration cacheTtl;
  private final boolean enabled;
  private final AtomicReference<AdminFrameworkVersionsResponse> snapshot = new AtomicReference<>();
  private final ReentrantLock refreshLock = new ReentrantLock();

  public FrameworkVersionMonitorService(
      FrameworkVersionInventory inventory,
      FrameworkVulnerabilityPort vulnerabilityPort,
      Clock clock,
      @Value("${app.version-monitor.cache-ttl:PT6H}") Duration cacheTtl,
      @Value("${app.version-monitor.enabled:true}") boolean enabled) {
    if (cacheTtl.isNegative() || cacheTtl.isZero()) {
      throw new IllegalArgumentException("app.version-monitor.cache-ttl must be positive");
    }
    this.inventory = inventory;
    this.vulnerabilityPort = vulnerabilityPort;
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
      AdminFrameworkVersionsResponse refreshed = buildSnapshot(
          frameworks,
          vulnerabilityPort.scan(frameworks),
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
      AdminFrameworkVersionsResponse previous) {
    Instant now = clock.instant();
    Map<String, AdminFrameworkVersionsResponse.Component> previousComponents =
        indexPreviousComponents(previous);
    List<AdminFrameworkVersionsResponse.Component> components = new ArrayList<>();
    int availableCount = 0;
    int completeCount = 0;

    for (MonitoredFramework framework : frameworks) {
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
            componentScan.complete() ? "current" : "partial"));
      } else {
        AdminFrameworkVersionsResponse.Component previousComponent =
            previousComponents.get(framework.key());
        if (previousComponent == null) {
          components.add(toComponent(framework, List.of(), "unavailable"));
        } else {
          components.add(copyAsStale(framework, previousComponent));
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
      String scanStatus) {
    List<AdminFrameworkVersionsResponse.Advisory> responseAdvisories = advisories.stream()
        .sorted(Comparator
            .comparing(FrameworkAdvisory::isCritical)
            .reversed()
            .thenComparing(FrameworkAdvisory::id))
        .map(this::toAdvisory)
        .toList();
    int criticalCount = (int) advisories.stream().filter(FrameworkAdvisory::isCritical).count();
    String securityStatus = criticalCount > 0
        ? "critical"
        : advisories.isEmpty() ? "safe" : "attention";
    return new AdminFrameworkVersionsResponse.Component(
        framework.key(),
        framework.displayName(),
        framework.ecosystem(),
        framework.packageName(),
        framework.currentVersion(),
        framework.versionSource(),
        scanStatus,
        securityStatus,
        advisories.size(),
        criticalCount,
        responseAdvisories);
  }

  private AdminFrameworkVersionsResponse.Component copyAsStale(
      MonitoredFramework framework,
      AdminFrameworkVersionsResponse.Component previous) {
    return new AdminFrameworkVersionsResponse.Component(
        framework.key(),
        framework.displayName(),
        framework.ecosystem(),
        framework.packageName(),
        framework.currentVersion(),
        framework.versionSource(),
        "stale",
        previous.securityStatus(),
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
    return new AdminFrameworkVersionsResponse(
        now,
        lastSuccessfulCheckAt,
        now.plus(cacheTtl),
        scanStatus,
        message,
        criticalCount > 0,
        vulnerabilityCount,
        criticalCount,
        components);
  }

  private AdminFrameworkVersionsResponse unavailableSnapshot(String message) {
    Instant now = clock.instant();
    List<AdminFrameworkVersionsResponse.Component> components = inventory.list().stream()
        .map(framework -> toComponent(framework, List.of(), "unavailable"))
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
        .map(framework -> toComponent(framework, List.of(), "disabled"))
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
