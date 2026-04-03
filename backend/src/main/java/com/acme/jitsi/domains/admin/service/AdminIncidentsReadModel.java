package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Instant;
import java.util.List;

public interface AdminIncidentsReadModel {

  List<IncidentSignal> loadSignals(SignalFilter filter);

  record SignalFilter(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      Instant from,
      Instant to,
      String roomId,
      String meetingId,
      String subjectId,
      String errorCode,
      String category,
      int sampleLimit) {
  }

  record IncidentSignal(
      Instant occurredAt,
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      String roomId,
      String meetingId,
      String subjectId,
      String traceId,
      String requestId,
      String errorCode,
      String category,
      String role,
      String diagnosticResult,
      String alertSeverity,
      String joinReadinessStatus) {
  }
}