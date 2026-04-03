package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminDashboardReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class AdminDashboardReadJpaAdapter implements AdminDashboardReadModel {

  private static final String BASE_SQL = """
      select
        audit.created_at,
        audit.room_id,
        audit.meeting_id,
        audit.subject_id,
        audit.trace_id,
        audit.action_type,
        audit.changed_fields
      from meeting_audit_events audit
      left join meetings meeting on meeting.meeting_id = audit.meeting_id and meeting.deleted = false
      left join rooms room on room.room_id = audit.room_id and room.deleted = false
      left join config_sets config on config.config_set_id = coalesce(meeting.config_set_id, room.config_set_id)
        and config.deleted = false
      where audit.created_at >= :from
        and audit.action_type in ('join_success', 'join_failed')
      """;

  private final EntityManager entityManager;
  private final AdminDashboardJoinAuditMapper auditMapper;

  @Autowired
  public AdminDashboardReadJpaAdapter(EntityManager entityManager) {
    this(entityManager, new AdminDashboardJoinAuditMapper());
  }

  AdminDashboardReadJpaAdapter(EntityManager entityManager, AdminDashboardJoinAuditMapper auditMapper) {
    this.entityManager = entityManager;
    this.auditMapper = auditMapper;
  }

  @Override
  public JoinAuditOverview loadJoinAuditOverview(DashboardFilter filter) {
    LoadedRows loadedRows = loadRows(filter.tenantId(), filter.environmentType(), filter.from(),
      filter.roomId(), filter.meetingId(), filter.sampleLimit());
    List<JoinAuditRecord> rows = loadedRows.rows();

    long successCount = rows.stream().filter(row -> row.errorCode() == null).count();
    long failureCount = rows.size() - successCount;
    List<Count> topCategories = summarize(rows.stream()
        .filter(row -> row.errorCode() != null)
        .map(row -> auditMapper.toDashboardReasonCategory(row.reasonCategory()))
      .filter(Objects::nonNull)
        .toList());
    List<Count> topErrorCodes = summarize(rows.stream()
        .filter(row -> row.errorCode() != null)
        .map(row -> normalize(row.errorCode(), "UNKNOWN"))
        .toList());
    List<JoinAuditRecord> recentFailures = rows.stream()
        .filter(row -> row.errorCode() != null)
        .limit(10)
        .toList();

    return new JoinAuditOverview(
        successCount,
        failureCount,
        topCategories,
        topErrorCodes,
        recentFailures,
        loadedRows.limited());
  }

  @Override
  public DrillDownOverview loadDrillDown(DrillDownFilter filter) {
    LoadedRows loadedRows = loadRows(filter.tenantId(), filter.environmentType(), filter.from(),
        filter.roomId(), filter.meetingId(), filter.sampleLimit());
    List<JoinAuditRecord> rows = loadedRows.rows().stream()
            .filter(row -> matchesSelection(row, filter.errorCode(), filter.reasonCategory()))
            .filter(row -> row.errorCode() != null)
            .toList();

    String selectionType = filter.errorCode() != null && !filter.errorCode().isBlank() ? "errorCode" : "category";
    String selectionValue = selectionType.equals("errorCode")
        ? filter.errorCode()
      : auditMapper.toDashboardReasonCategory(filter.reasonCategory());
    List<JoinAuditRecord> recentFailures = rows.stream().limit(20).toList();
    return new DrillDownOverview(selectionType, selectionValue, rows.size(), recentFailures, loadedRows.limited());
  }

  private boolean matchesSelection(JoinAuditRecord row, String errorCode, String reasonCategory) {
    if (errorCode != null && !errorCode.isBlank()) {
      return errorCode.equalsIgnoreCase(normalize(row.errorCode(), ""));
    }
    if (reasonCategory != null && !reasonCategory.isBlank()) {
      String requestedCategory = auditMapper.toDashboardReasonCategory(reasonCategory);
      return requestedCategory != null && requestedCategory.equals(auditMapper.toDashboardReasonCategory(row.reasonCategory()));
    }
    return true;
  }

  private LoadedRows loadRows(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      Instant from,
      String roomId,
      String meetingId,
      int sampleLimit) {
    List<NativeFilter> filters = new ArrayList<>();
    addTextFilter(filters, tenantId, " and room.tenant_id = :tenantId", "tenantId");
    addTextFilter(filters, roomId, " and audit.room_id = :roomId", "roomId");
    addTextFilter(filters, meetingId, " and audit.meeting_id = :meetingId", "meetingId");
    addEnvironmentFilter(filters, environmentType);

    StringBuilder sql = new StringBuilder(BASE_SQL);
    filters.forEach(filter -> sql.append(filter.sqlClause()));
    sql.append(" order by audit.created_at desc");

    Query query = entityManager.createNativeQuery(sql.toString());
    query.setParameter("from", Timestamp.from(from));
    filters.forEach(filter -> filter.bind(query));
    query.setMaxResults(sampleLimit + 1);

    @SuppressWarnings("unchecked")
    List<Object[]> rawRows = query.getResultList();
    List<JoinAuditRecord> rows = rawRows.stream()
      .limit(sampleLimit)
      .map(this::toRecord)
      .toList();
    return new LoadedRows(rows, rawRows.size() > sampleLimit);
  }

  private JoinAuditRecord toRecord(Object[] row) {
    return auditMapper.toRecord(row);
  }

  private void addTextFilter(List<NativeFilter> filters, String value, String sqlClause, String parameterName) {
    if (value != null && !value.isBlank()) {
      filters.add(new NativeFilter(sqlClause, query -> query.setParameter(parameterName, value)));
    }
  }

  private void addEnvironmentFilter(List<NativeFilter> filters, ConfigSetEnvironmentType environmentType) {
    if (environmentType != null) {
      filters.add(new NativeFilter(
          " and config.environment_type = :environmentType",
          query -> query.setParameter("environmentType", environmentType.name())));
    }
  }

  private List<Count> summarize(List<String> keys) {
    Map<String, Long> counts = keys.stream()
        .filter(Objects::nonNull)
        .collect(Collectors.groupingBy(key -> key, LinkedHashMap::new, Collectors.counting()));
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
            .thenComparing(Map.Entry.comparingByKey()))
        .limit(6)
          .map(entry -> new Count(entry.getKey(), entry.getValue()))
        .toList();
  }

  private String normalize(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private record LoadedRows(List<JoinAuditRecord> rows, boolean limited) {
  }

  @FunctionalInterface
  private interface QueryBinder {
    void bind(Query query);
  }

  private record NativeFilter(String sqlClause, QueryBinder binder) {
    private void bind(Query query) {
      binder.bind(query);
    }
  }
}