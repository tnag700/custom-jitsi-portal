package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class AdminIncidentViewSupport {

  private static final String SEVERITY_CRITICAL = "critical";

  private AdminIncidentViewSupport() {
  }

  static String affectedEntitySummary(AdminIncidentAggregate incident) {
    long affectedSubjects = incident.affectedSubjects().size();
    if (incident.roomId() != null && incident.meetingId() != null) {
      return "Комната %s, встреча %s, %d затронутых субъекта".formatted(
          incident.roomId(),
          incident.meetingId(),
          affectedSubjects);
    }
    if (incident.roomId() != null) {
      return "Комната %s, %d затронутых субъекта".formatted(
          incident.roomId(),
          affectedSubjects);
    }
    if (incident.meetingId() != null) {
      return "Встреча %s, %d затронутых субъекта".formatted(
          incident.meetingId(),
          affectedSubjects);
    }
    return "%d затронутых субъекта без явной room/meeting привязки"
        .formatted(affectedSubjects);
  }

  static String freshnessHint(AdminIncidentAggregate incident, Clock clock) {
    Duration age = Duration.between(incident.lastOccurredAt(), Instant.now(clock));
    String relative = formatRelativeAge(age);
    boolean spikeOrCritical = incident.signals().size() >= 3
        || SEVERITY_CRITICAL.equalsIgnoreCase(incident.severity());
    return (spikeOrCritical ? "Последний всплеск %s" : "Активность %s")
        .formatted(relative);
  }

  static String operationalStatus(AdminIncidentAggregate incident) {
    boolean blocked = incident.signals().stream()
        .anyMatch(signal -> "blocked".equalsIgnoreCase(
            IncidentNormalizationPolicy.blankToNull(signal.joinReadinessStatus())));
    if (blocked) {
      return "blocked";
    }
    if (incident.signals().isEmpty()) {
      return "monitoring";
    }
    return "active-investigation";
  }

  static String timelineSummary(AdminIncidentDetailResponse.AffectedAttempt attempt) {
    List<String> parts = new ArrayList<>();
    if (IncidentNormalizationPolicy.hasText(attempt.role())) {
      parts.add(attempt.role());
    }
    if (IncidentNormalizationPolicy.hasText(attempt.subjectDisplay())) {
      parts.add(attempt.subjectDisplay());
    }
    if (parts.isEmpty()) {
      return "Детализация попытки входа доступна в технических деталях";
    }
    return String.join(" · ", parts);
  }

  private static String formatRelativeAge(Duration age) {
    long minutes = Math.max(age.toMinutes(), 0L);
    if (minutes < 1) {
      return "только что";
    }
    if (minutes < 60) {
      return "%d мин назад".formatted(minutes);
    }
    long hours = Math.max(age.toHours(), 1L);
    if (hours < 24) {
      return "%d ч назад".formatted(hours);
    }
    return "%d дн назад".formatted(Math.max(age.toDays(), 1L));
  }
}
