package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSet;
import com.acme.jitsi.domains.configsets.service.ConfigSetCompatibilityCheck;
import com.acme.jitsi.domains.health.dto.HealthResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessCheckResponse;
import com.acme.jitsi.domains.health.dto.JoinReadinessResponse;
import java.util.List;

final class AdminDashboardSummarySignalSupport {

  private static final String SEVERITY_INFO = "info";
  private static final String SEVERITY_WARNING = "warning";
  private static final String SEVERITY_CRITICAL = "critical";

  String buildCompatibilitySummary(ConfigSet activeConfigSet, ConfigSetCompatibilityCheck compatibility) {
    String summary;
    if (compatibility == null) {
      summary = "Для config set %s нет свежего compatibility snapshot. Оператору нужен handoff в incident queue.".formatted(
          activeConfigSet.configSetId());
    } else if (compatibility.compatible()) {
      summary = "Compatibility snapshot не сигнализирует проблем для %s.".formatted(activeConfigSet.configSetId());
    } else {
      summary = "Config set %s несовместим: %s.".formatted(
          activeConfigSet.configSetId(),
          compatibility.details());
    }
    return summary;
  }

  String configErrorCode(boolean incompatible) {
    return incompatible ? "CONFIG_INCOMPATIBLE" : null;
  }

  String severityForHealth(HealthResponse health) {
    return "UP".equalsIgnoreCase(health.status()) ? SEVERITY_INFO : SEVERITY_CRITICAL;
  }

  String severityForJoinStatus(String joinStatus) {
    return switch (joinStatus) {
      case "blocked" -> SEVERITY_CRITICAL;
      case "degraded" -> SEVERITY_WARNING;
      default -> SEVERITY_INFO;
    };
  }

  String primaryJoinSummary(JoinReadinessResponse joinReadiness) {
    return joinReadiness.systemChecks().stream()
        .filter(check -> check.blocking() || !"ok".equalsIgnoreCase(check.status()))
        .findFirst()
        .map(check -> check.reason() == null || check.reason().isBlank() ? check.headline() : check.reason())
        .orElse("Join readiness сообщает деградацию, требующую расследования в incident queue.");
  }

  String primaryJoinErrorCode(List<JoinReadinessCheckResponse> systemChecks) {
    return systemChecks.stream()
        .filter(check -> check.errorCode() != null && !check.errorCode().isBlank())
        .findFirst()
        .map(JoinReadinessCheckResponse::errorCode)
        .orElse(null);
  }

  String findCategoryForErrorCode(AdminDashboardReadModel.JoinAuditOverview overview, String errorCode) {
    String fallbackCategory = overview.topCategories().isEmpty() ? null : overview.topCategories().get(0).key();
    String category = fallbackCategory;
    if (errorCode != null && !errorCode.isBlank()) {
      category = overview.recentFailures().stream()
          .filter(record -> errorCode.equals(record.errorCode()))
          .map(AdminDashboardReadModel.JoinAuditRecord::reasonCategory)
          .filter(value -> value != null && !value.isBlank())
          .findFirst()
          .orElse(fallbackCategory);
    }
    return category;
  }

  int severityRank(String severity) {
    return switch (severity == null ? "" : severity) {
      case SEVERITY_CRITICAL -> 0;
      case SEVERITY_WARNING -> 1;
      case SEVERITY_INFO -> 2;
      default -> 3;
    };
  }

  int degradationPriority(String id) {
    return switch (id == null ? "" : id) {
      case "config-compatibility" -> 0;
      case "join-readiness" -> 1;
      case "failure-spike" -> 2;
      case "backend-api" -> 3;
      default -> 4;
    };
  }
}