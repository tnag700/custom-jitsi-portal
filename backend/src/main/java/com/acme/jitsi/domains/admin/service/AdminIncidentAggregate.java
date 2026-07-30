package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Instant;
import java.util.List;
import java.util.Set;

record AdminIncidentAggregate(
    String incidentId,
    String tenantId,
    ConfigSetEnvironmentType environmentType,
    String errorCode,
    String category,
    String roomId,
    String meetingId,
    Instant firstOccurredAt,
    Instant lastOccurredAt,
    List<AdminIncidentsReadModel.IncidentSignal> signals,
    Set<String> affectedSubjects,
    String severity) {
}
