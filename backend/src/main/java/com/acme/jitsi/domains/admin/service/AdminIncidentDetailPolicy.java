package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class AdminIncidentDetailPolicy {

  private AdminIncidentDetailPolicy() {
  }

  static AdminIncidentDetailResponse resolveResponse(
      AdminIncidentAggregate incident,
      String tenantId,
      boolean fullSubject,
      String traceUrlTemplate,
      AdminIncidentCoordinationPort.CoordinationSnapshot coordination,
      AdminIncidentTicketPort.TicketingStatus ticketing) {
    List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts =
        buildAffectedAttempts(incident, fullSubject, traceUrlTemplate);
    List<AdminIncidentDetailResponse.RelatedLink> relatedLinks =
        buildRelatedLinks(incident, affectedAttempts);
    List<AdminIncidentDetailResponse.EvidenceBlock> evidence =
        buildEvidence(affectedAttempts, relatedLinks);
    return new AdminIncidentDetailResponse(
        incident.incidentId(),
        tenantId,
        IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()),
        incident.errorCode(),
        incident.category(),
        incident.severity(),
        buildSummary(incident),
        incident.firstOccurredAt().toString(),
        incident.lastOccurredAt().toString(),
        affectedAttempts,
        buildSummaryBar(incident),
        buildTimeline(affectedAttempts),
        evidence,
        relatedLinks,
        buildNextActions(relatedLinks, evidence),
        toCoordinationState(coordination),
        new AdminIncidentDetailResponse.TicketingState(
            ticketing.available(),
            IncidentNormalizationPolicy.firstNonBlank(
                coordination.ticketReference(),
                ticketing.ticketKey()),
            IncidentNormalizationPolicy.firstNonBlank(
                coordination.ticketUrl(),
                ticketing.ticketUrl()),
            IncidentNormalizationPolicy.firstNonBlank(
                coordination.ticketStatus(),
                ticketing.status())));
  }

  static AdminIncidentTicketPort.TicketContext toTicketContext(
      AdminIncidentAggregate incident) {
    return new AdminIncidentTicketPort.TicketContext(
        incident.incidentId(),
        incident.tenantId(),
        IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()),
        incident.errorCode(),
        incident.category(),
        incidentTraceReference(incident),
        buildSummary(incident));
  }

  static AdminIncidentCoordinationPort.CoordinationContext toCoordinationContext(
      AdminIncidentAggregate incident) {
    return new AdminIncidentCoordinationPort.CoordinationContext(
        incident.incidentId(),
        incident.tenantId(),
        IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()));
  }

  static AdminIncidentDetailResponse.CoordinationState toCoordinationState(
      AdminIncidentCoordinationPort.CoordinationSnapshot snapshot) {
    return new AdminIncidentDetailResponse.CoordinationState(
        snapshot.enabled(),
        snapshot.availability(),
        snapshot.explanation(),
        snapshot.owner(),
        snapshot.workflowStatus(),
        snapshot.ticketReference(),
        snapshot.ticketStatus(),
        snapshot.ticketUrl(),
        snapshot.history().stream()
            .map(entry -> new AdminIncidentDetailResponse.CoordinationAuditEntry(
                entry.occurredAt(),
                entry.actorId(),
                entry.actionType(),
                entry.traceId(),
                entry.fromState(),
                entry.toState()))
            .toList());
  }

  static String incidentTraceReference(AdminIncidentAggregate incident) {
    return incident.signals().stream()
        .map(signal -> IncidentNormalizationPolicy.firstNonBlank(
            signal.traceId(),
            signal.requestId()))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  private static List<AdminIncidentDetailResponse.AffectedAttempt> buildAffectedAttempts(
      AdminIncidentAggregate incident,
      boolean fullSubject,
      String traceUrlTemplate) {
    return incident.signals().stream()
        .sorted(Comparator.comparing(
            AdminIncidentsReadModel.IncidentSignal::occurredAt).reversed())
        .map(signal -> new AdminIncidentDetailResponse.AffectedAttempt(
            signal.occurredAt().toString(),
            signal.traceId(),
            IncidentNormalizationPolicy.firstNonBlank(
                signal.requestId(),
                signal.traceId()),
            fullSubject
                ? IncidentNormalizationPolicy.blankToNull(signal.subjectId())
                : IncidentSubjectPolicy.maskSubject(signal.subjectId()),
            subjectFilterValue(signal, fullSubject),
            IncidentNormalizationPolicy.blankToNull(signal.role()),
            IncidentNormalizationPolicy.blankToNull(signal.diagnosticResult()),
            IncidentNormalizationPolicy.blankToNull(signal.roomId()),
            IncidentNormalizationPolicy.blankToNull(signal.meetingId()),
            buildTraceUrl(signal.traceId(), traceUrlTemplate)))
        .toList();
  }

  private static String subjectFilterValue(
      AdminIncidentsReadModel.IncidentSignal signal,
      boolean fullSubject) {
    return fullSubject
        ? IncidentNormalizationPolicy.blankToNull(signal.subjectId())
        : null;
  }

  private static AdminIncidentDetailResponse.SummaryBar buildSummaryBar(
      AdminIncidentAggregate incident) {
    return new AdminIncidentDetailResponse.SummaryBar(
        "%s incident".formatted(incident.errorCode()),
        "%s / %s".formatted(incident.errorCode(), incident.category()),
        AdminIncidentViewSupport.affectedEntitySummary(incident),
        AdminIncidentViewSupport.operationalStatus(incident),
        "%s → %s".formatted(
            incident.firstOccurredAt(),
            incident.lastOccurredAt()),
        IncidentEnvironmentPolicy.environmentLabel(incident.environmentType()));
  }

  private static List<AdminIncidentDetailResponse.TimelineEntry> buildTimeline(
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
    return affectedAttempts.stream()
        .map(attempt -> new AdminIncidentDetailResponse.TimelineEntry(
            attempt.occurredAt(),
            "Повторный отказ входа",
            AdminIncidentViewSupport.timelineSummary(attempt),
            attempt.subjectDisplay(),
            attempt.role(),
            attempt.traceId(),
            attempt.correlationId(),
            attempt.roomId(),
            attempt.meetingId()))
        .toList();
  }

  private static List<AdminIncidentDetailResponse.EvidenceBlock> buildEvidence(
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts,
      List<AdminIncidentDetailResponse.RelatedLink> relatedLinks) {
    AdminIncidentDetailResponse.AffectedAttempt diagnosticsAttempt =
        findAttemptWithDiagnostics(affectedAttempts);
    AdminIncidentDetailResponse.AffectedAttempt correlationAttempt =
        findAttemptWithCorrelation(affectedAttempts);
    return List.of(
        buildDiagnosticsEvidence(diagnosticsAttempt),
        buildCorrelationEvidence(correlationAttempt, relatedLinks));
  }

  private static AdminIncidentDetailResponse.AffectedAttempt findAttemptWithDiagnostics(
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
    return affectedAttempts.stream()
        .filter(attempt ->
            IncidentNormalizationPolicy.hasText(attempt.diagnosticResult()))
        .findFirst()
        .orElseGet(() -> firstAttemptOrNull(affectedAttempts));
  }

  private static AdminIncidentDetailResponse.AffectedAttempt findAttemptWithCorrelation(
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
    return affectedAttempts.stream()
        .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.traceId())
            || IncidentNormalizationPolicy.hasText(attempt.correlationId()))
        .findFirst()
        .orElseGet(() -> firstAttemptOrNull(affectedAttempts));
  }

  private static AdminIncidentDetailResponse.AffectedAttempt firstAttemptOrNull(
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
    return affectedAttempts.stream().findFirst().orElse(null);
  }

  private static AdminIncidentDetailResponse.EvidenceBlock buildDiagnosticsEvidence(
      AdminIncidentDetailResponse.AffectedAttempt attempt) {
    if (attempt == null
        || !IncidentNormalizationPolicy.hasText(attempt.diagnosticResult())) {
      return new AdminIncidentDetailResponse.EvidenceBlock(
          "diagnostics",
          "Diagnostics result",
          "empty",
          null,
          "Диагностический результат для инцидента не был зафиксирован в bounded read model.",
          attempt == null ? null : attempt.traceId(),
          attempt == null ? null : attempt.correlationId(),
          attempt == null ? null : attempt.traceUrl(),
          new AdminIncidentDetailResponse.EmptyState(
              "Нет diagnostics result",
              "Используйте trace/correlation context или role history как следующий bounded источник доказательств.",
              "Открыть историю ролей",
              "role-history"));
    }

    return new AdminIncidentDetailResponse.EvidenceBlock(
        "diagnostics",
        "Diagnostics result",
        "available",
        attempt.diagnosticResult(),
        "Diagnostics evidence подготовлен для first-scan без raw payload dump.",
        attempt.traceId(),
        attempt.correlationId(),
        attempt.traceUrl(),
        null);
  }

  private static AdminIncidentDetailResponse.EvidenceBlock buildCorrelationEvidence(
      AdminIncidentDetailResponse.AffectedAttempt attempt,
      List<AdminIncidentDetailResponse.RelatedLink> relatedLinks) {
    AdminIncidentDetailResponse.RelatedLink traceLink = relatedLinks.stream()
        .filter(link -> "trace".equals(link.kind()))
        .findFirst()
        .orElse(null);
    boolean noCorrelation = attempt == null
        || (!IncidentNormalizationPolicy.hasText(attempt.traceId())
            && !IncidentNormalizationPolicy.hasText(attempt.correlationId()));

    if (noCorrelation) {
      return new AdminIncidentDetailResponse.EvidenceBlock(
          "correlation",
          "Trace и correlation context",
          "empty",
          null,
          "Для этого инцидента отсутствует trace или correlation identifier, поэтому UI должен показать bounded empty state вместо пустой секции.",
          null,
          null,
          null,
          new AdminIncidentDetailResponse.EmptyState(
              "Нет trace link",
              "Откройте role history или вернитесь в incident queue, чтобы продолжить расследование по связанным сущностям.",
              "Вернуться в очередь",
              "queue-return"));
    }

    AdminIncidentDetailResponse.AffectedAttempt correlationAttempt =
        Objects.requireNonNull(attempt);
    if (traceLink != null
        && IncidentNormalizationPolicy.hasText(traceLink.externalUrl())) {
      return new AdminIncidentDetailResponse.EvidenceBlock(
          "correlation",
          "Trace и correlation context",
          "available",
          IncidentNormalizationPolicy.firstNonBlank(
              correlationAttempt.traceId(),
              correlationAttempt.correlationId()),
          "Trace link уже нормализован и готов для drill-through без ручного повторного поиска.",
          correlationAttempt.traceId(),
          correlationAttempt.correlationId(),
          traceLink.externalUrl(),
          null);
    }

    return new AdminIncidentDetailResponse.EvidenceBlock(
        "correlation",
        "Trace и correlation context",
        "copy-only",
        IncidentNormalizationPolicy.firstNonBlank(
            correlationAttempt.traceId(),
            correlationAttempt.correlationId()),
        "Trace URL недоступен, но trace/correlation data остаются пригодными для копирования и bounded ручного drill-through.",
        correlationAttempt.traceId(),
        correlationAttempt.correlationId(),
        null,
        null);
  }

  private static List<AdminIncidentDetailResponse.RelatedLink> buildRelatedLinks(
      AdminIncidentAggregate incident,
      List<AdminIncidentDetailResponse.AffectedAttempt> affectedAttempts) {
    List<AdminIncidentDetailResponse.RelatedLink> links = new ArrayList<>();
    AdminIncidentDetailResponse.AffectedAttempt historyAttempt =
        affectedAttempts.stream()
            .filter(attempt ->
                IncidentNormalizationPolicy.hasText(
                    attempt.subjectIdFilterValue())
                    || IncidentNormalizationPolicy.hasText(attempt.roomId())
                    || IncidentNormalizationPolicy.hasText(attempt.meetingId()))
            .findFirst()
            .orElse(null);
    if (historyAttempt != null) {
      links.add(new AdminIncidentDetailResponse.RelatedLink(
          "role-history",
          "История ролей по субъекту",
          IncidentEnvironmentPolicy.environmentLabel(
              incident.environmentType()),
          historyAttempt.subjectIdFilterValue(),
          historyAttempt.roomId(),
          historyAttempt.meetingId(),
          historyAttempt.traceId(),
          null));
    }

    AdminIncidentDetailResponse.AffectedAttempt scopeAttempt =
        affectedAttempts.stream()
            .filter(attempt ->
                IncidentNormalizationPolicy.hasText(attempt.roomId())
                    || IncidentNormalizationPolicy.hasText(attempt.meetingId()))
            .findFirst()
            .orElse(null);
    if (scopeAttempt != null) {
      links.add(new AdminIncidentDetailResponse.RelatedLink(
          "incident-scope",
          "Очередь по затронутой сущности",
          IncidentEnvironmentPolicy.environmentLabel(
              incident.environmentType()),
          scopeAttempt.subjectIdFilterValue(),
          scopeAttempt.roomId(),
          scopeAttempt.meetingId(),
          scopeAttempt.traceId(),
          null));
    }

    affectedAttempts.stream()
        .filter(attempt -> IncidentNormalizationPolicy.hasText(attempt.traceUrl()))
        .findFirst()
        .ifPresent(traceAttempt -> links.add(
            new AdminIncidentDetailResponse.RelatedLink(
                "trace",
                "Открыть trace",
                IncidentEnvironmentPolicy.environmentLabel(
                    incident.environmentType()),
                traceAttempt.subjectIdFilterValue(),
                traceAttempt.roomId(),
                traceAttempt.meetingId(),
                traceAttempt.traceId(),
                traceAttempt.traceUrl())));
    return List.copyOf(links);
  }

  private static List<AdminIncidentDetailResponse.NextAction> buildNextActions(
      List<AdminIncidentDetailResponse.RelatedLink> relatedLinks,
      List<AdminIncidentDetailResponse.EvidenceBlock> evidence) {
    List<AdminIncidentDetailResponse.NextAction> actions = new ArrayList<>();
    actions.add(new AdminIncidentDetailResponse.NextAction(
        "queue",
        "Вернуться в очередь",
        "Сохранить incident context и продолжить triage из queue-first surface.",
        "queue-return",
        null));

    relatedLinks.stream()
        .filter(link -> "role-history".equals(link.kind()))
        .findFirst()
        .ifPresent(link -> actions.add(
            new AdminIncidentDetailResponse.NextAction(
                "role-history",
                "Открыть историю ролей",
                "Перейти к связанному role-history context без ручного воспроизведения filters.",
                "role-history",
                null)));
    relatedLinks.stream()
        .filter(link -> "trace".equals(link.kind())
            && IncidentNormalizationPolicy.hasText(link.externalUrl()))
        .findFirst()
        .ifPresent(link -> actions.add(
            new AdminIncidentDetailResponse.NextAction(
                "trace",
                "Открыть trace",
                "Перейти в trace tool с уже сохранённым incident context.",
                "external-trace",
                link.externalUrl())));
    if (evidence.stream().anyMatch(
        block -> "correlation".equals(block.kind())
            && "copy-only".equals(block.status()))) {
      actions.add(new AdminIncidentDetailResponse.NextAction(
          "correlation",
          "Использовать trace/correlation data",
          "Скопируйте trace или correlation identifier и продолжите bounded drill-through вручную.",
          "copy-correlation",
          null));
    }
    return List.copyOf(actions);
  }

  private static String buildSummary(AdminIncidentAggregate incident) {
    return "%s incident for %s (%s)".formatted(
        incident.errorCode(),
        incident.tenantId(),
        incident.severity());
  }

  private static String buildTraceUrl(
      String traceId,
      String traceUrlTemplate) {
    if (!IncidentNormalizationPolicy.hasText(traceId)
        || !IncidentNormalizationPolicy.hasText(traceUrlTemplate)) {
      return null;
    }
    return traceUrlTemplate.replace("{traceId}", traceId);
  }
}
