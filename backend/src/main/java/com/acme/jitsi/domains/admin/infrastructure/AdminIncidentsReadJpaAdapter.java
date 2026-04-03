package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentsReadModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AdminIncidentsReadJpaAdapter implements AdminIncidentsReadModel {

  private final EntityManager entityManager;
  private final AdminIncidentsSignalQuerySupport querySupport = new AdminIncidentsSignalQuerySupport();
  private final AdminIncidentsSignalMapper signalMapper = new AdminIncidentsSignalMapper();

  public AdminIncidentsReadJpaAdapter(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Override
  public List<IncidentSignal> loadSignals(SignalFilter filter) {
    List<IncidentSignal> signals = new ArrayList<>();
    signals.addAll(loadMeetingSignals(filter));
    signals.addAll(loadAuthSignals(filter));
    signals.addAll(loadConfigSignals(filter));
    return signals.stream()
        .sorted(Comparator.comparing(IncidentSignal::occurredAt).reversed())
        .limit(filter.sampleLimit())
        .toList();
  }

  private List<IncidentSignal> loadMeetingSignals(SignalFilter filter) {
    StringBuilder sql = new StringBuilder("""
        select
          audit.created_at,
          room.tenant_id,
          config.environment_type,
          audit.room_id,
          audit.meeting_id,
          audit.subject_id,
          audit.trace_id,
          audit.changed_fields
        from meeting_audit_events audit
        left join meetings meeting on meeting.meeting_id = audit.meeting_id and meeting.deleted = false
        left join rooms room on room.room_id = audit.room_id and room.deleted = false
        left join config_sets config on config.config_set_id = coalesce(meeting.config_set_id, room.config_set_id)
          and config.deleted = false
        where audit.created_at between :from and :to
          and audit.action_type = 'join_failed'
        """);
    querySupport.appendSharedFilters(sql, filter, "room.tenant_id", "config.environment_type", "audit.room_id", "audit.meeting_id", "audit.subject_id");
    querySupport.appendMeetingSignalFilters(sql, filter);
    sql.append(" order by audit.created_at desc");

    Query query = entityManager.createNativeQuery(sql.toString());
    querySupport.bindSharedFilters(query, filter);
    querySupport.bindMeetingSignalFilters(query, filter);
    query.setMaxResults(filter.sampleLimit());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    return rows.stream().map(signalMapper::toMeetingSignal).toList();
  }

  private List<IncidentSignal> loadAuthSignals(SignalFilter filter) {
    StringBuilder sql = new StringBuilder("""
        select
          audit.occurred_at,
          coalesce(audit.tenant_id, room.tenant_id),
          config.environment_type,
          room.room_id,
          audit.meeting_id,
          audit.subject_id,
          audit.trace_id,
          audit.error_code,
          audit.client_context,
          audit.event_type
        from auth_audit_events audit
        left join meetings meeting on meeting.meeting_id = audit.meeting_id and meeting.deleted = false
        left join rooms room on room.room_id = meeting.room_id and room.deleted = false
        left join config_sets config on config.config_set_id = coalesce(meeting.config_set_id, room.config_set_id)
          and config.deleted = false
        where audit.occurred_at between :from and :to
          and audit.error_code is not null
        """);
    querySupport.appendSharedFilters(sql, filter, "coalesce(audit.tenant_id, room.tenant_id)", "config.environment_type", "room.room_id", "audit.meeting_id", "audit.subject_id");
    if (filter.errorCode() != null) {
      sql.append(" and upper(audit.error_code) = :errorCode");
    }
    querySupport.appendAuthCategoryFilter(sql, filter.category());
    sql.append(" order by audit.occurred_at desc");

    Query query = entityManager.createNativeQuery(sql.toString());
    querySupport.bindSharedFilters(query, filter);
    query.setMaxResults(filter.sampleLimit());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    return rows.stream().map(signalMapper::toAuthSignal).toList();
  }

  private List<IncidentSignal> loadConfigSignals(SignalFilter filter) {
    StringBuilder sql = new StringBuilder("""
        select
          checks.checked_at,
          config.tenant_id,
          config.environment_type,
          checks.trace_id,
          checks.details
        from config_set_compatibility_checks checks
        join config_sets config on config.config_set_id = checks.config_set_id and config.deleted = false
        where checks.checked_at between :from and :to
          and checks.compatible = false
        """);
    querySupport.appendConfigFilters(sql, filter);
    sql.append(" order by checks.checked_at desc");

    Query query = entityManager.createNativeQuery(sql.toString());
    querySupport.bindConfigFilters(query, filter);
    query.setMaxResults(filter.sampleLimit());

    @SuppressWarnings("unchecked")
    List<Object[]> rows = query.getResultList();
    return rows.stream().map(signalMapper::toConfigSignal).toList();
  }
}