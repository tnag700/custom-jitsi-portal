package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminRoleHistoryReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
public class AdminRoleHistoryReadJpaAdapter implements AdminRoleHistoryReadModel {

  private static final String SELECT_CLAUSE = """
      select
        audit.id,
        audit.created_at,
        room.tenant_id,
        coalesce(config.environment_type, 'DEV'),
        audit.room_id,
        audit.meeting_id,
        audit.actor_id,
        audit.subject_id,
        audit.trace_id,
        audit.action_type,
        audit.changed_fields,
        subject_profile.full_name,
        actor_profile.full_name
      from meeting_audit_events audit
      join rooms room on room.room_id = audit.room_id and room.deleted = false
      left join meetings meeting on meeting.meeting_id = audit.meeting_id and meeting.deleted = false
      left join config_sets config on config.config_set_id = coalesce(meeting.config_set_id, room.config_set_id)
        and config.deleted = false
      left join user_profiles subject_profile on subject_profile.subject_id = audit.subject_id and subject_profile.tenant_id = room.tenant_id
      left join user_profiles actor_profile on actor_profile.subject_id = audit.actor_id and actor_profile.tenant_id = room.tenant_id
      where room.tenant_id = :tenantId
        and audit.action_type in ('assign', 'update', 'unassign')
        and audit.created_at between :from and :to
      """;

  private final EntityManager entityManager;

  public AdminRoleHistoryReadJpaAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public PageResult loadHistory(Filter filter) {
    List<SqlFilter> sqlFilters = collectFilters(filter);
    StringBuilder whereClause = new StringBuilder(SELECT_CLAUSE);
    bindFilters(whereClause, sqlFilters);

    Query countQuery = entityManager.createNativeQuery("select count(*) from (" + whereClause + ") role_history_count");
    setParameters(countQuery, filter, sqlFilters);
    long totalElements = ((Number) countQuery.getSingleResult()).longValue();

    String sql = whereClause + " order by audit.created_at desc, audit.id desc";
    Query dataQuery = entityManager.createNativeQuery(sql);
    setParameters(dataQuery, filter, sqlFilters);
    dataQuery.setFirstResult(filter.page() * filter.pageSize());
    dataQuery.setMaxResults(filter.pageSize());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = dataQuery.getResultList();
    return new PageResult(rows.stream().map(this::toRow).toList(), totalElements);
  }

  private void bindFilters(StringBuilder sql, List<SqlFilter> sqlFilters) {
    sqlFilters.forEach(filter -> sql.append(filter.sqlClause()));
  }

  private void setParameters(Query query, Filter filter, List<SqlFilter> sqlFilters) {
    query.setParameter("tenantId", filter.tenantId());
    query.setParameter("from", Timestamp.from(filter.from()));
    query.setParameter("to", Timestamp.from(filter.to()));
    sqlFilters.forEach(sqlFilter -> sqlFilter.bind(query));
  }

  private List<SqlFilter> collectFilters(Filter filter) {
    List<SqlFilter> sqlFilters = new ArrayList<>();
    if (filter.environmentType() != null) {
      sqlFilters.add(new SqlFilter(
          " and config.environment_type = :environmentType",
          query -> query.setParameter("environmentType", filter.environmentType().name())));
    }
    if (filter.actionType() != null) {
      sqlFilters.add(new SqlFilter(
          " and audit.action_type = :actionType",
          query -> query.setParameter("actionType", filter.actionType())));
    }
    if (filter.role() != null) {
      String loweredRole = filter.role().toLowerCase();
      sqlFilters.add(new SqlFilter(
          " and (lower(audit.changed_fields) like :roleFromPattern or lower(audit.changed_fields) like :roleToPattern)",
          query -> {
            query.setParameter("roleFromPattern", "%role:" + loweredRole + "->%");
            query.setParameter("roleToPattern", "%->" + loweredRole + "%");
          }));
    }
    addTextFilter(sqlFilters, filter.actorId(), " and audit.actor_id = :actorId", "actorId");
    addTextFilter(sqlFilters, filter.subjectId(), " and audit.subject_id = :subjectId", "subjectId");
    addTextFilter(sqlFilters, filter.roomId(), " and audit.room_id = :roomId", "roomId");
    addTextFilter(sqlFilters, filter.meetingId(), " and audit.meeting_id = :meetingId", "meetingId");
    addTextFilter(sqlFilters, filter.traceId(), " and audit.trace_id = :traceId", "traceId");
    if (filter.query() != null) {
      sqlFilters.add(new SqlFilter(
          " and (lower(audit.subject_id) like :queryPattern or lower(coalesce(subject_profile.full_name, '')) like :queryPattern)",
          query -> query.setParameter("queryPattern", "%" + filter.query().toLowerCase() + "%")));
    }
    return sqlFilters;
  }

  private void addTextFilter(List<SqlFilter> sqlFilters, @Nullable String value, String sqlClause, String parameterName) {
    if (value != null) {
      sqlFilters.add(new SqlFilter(sqlClause, query -> query.setParameter(parameterName, value)));
    }
  }

  private RoleHistoryRow toRow(Object[] row) {
    RoleRoles roles = parseRoles(value(row[10]));
    return new RoleHistoryRow(
        toInstant(row[1]),
        value(row[9]),
        roles.oldRole(),
        roles.newRole(),
        value(row[2]),
        ConfigSetEnvironmentType.valueOf(value(row[3])),
        value(row[4]),
        value(row[5]),
        value(row[6]),
        value(row[12]),
        value(row[7]),
        value(row[11]),
        value(row[8]));
  }

  private RoleRoles parseRoles(@Nullable String changedFields) {
    if (changedFields == null || changedFields.isBlank()) {
      return new RoleRoles(null, null);
    }
    String[] fragments = changedFields.split(";");
    for (String fragment : fragments) {
      if (!fragment.startsWith("role:")) {
        continue;
      }
      String values = fragment.substring("role:".length());
      int separator = values.indexOf("->");
      if (separator < 0) {
        return new RoleRoles(null, null);
      }
      return new RoleRoles(normalizeRole(values.substring(0, separator)), normalizeRole(values.substring(separator + 2)));
    }
    return new RoleRoles(null, null);
  }

  private @Nullable String normalizeRole(String value) {
    if (value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim())) {
      return null;
    }
    return value.trim().toLowerCase();
  }

  private String value(@Nullable Object raw) {
    return raw == null ? null : raw.toString();
  }

  private Instant toInstant(Object raw) {
    if (raw instanceof Instant instant) {
      return instant;
    }
    if (raw instanceof Timestamp timestamp) {
      return timestamp.toInstant();
    }
    if (raw instanceof OffsetDateTime offsetDateTime) {
      return offsetDateTime.toInstant();
    }
    return Instant.parse(raw.toString());
  }

  private record RoleRoles(@Nullable String oldRole, @Nullable String newRole) {
  }

  @FunctionalInterface
  private interface QueryBinder {
    void bind(Query query);
  }

  private record SqlFilter(String sqlClause, QueryBinder binder) {
    private void bind(Query query) {
      binder.bind(query);
    }
  }
}