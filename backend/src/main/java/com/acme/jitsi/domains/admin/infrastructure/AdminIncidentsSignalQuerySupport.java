package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentsReadModel;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.util.Locale;

final class AdminIncidentsSignalQuerySupport {

  void appendSharedFilters(
      StringBuilder sql,
      AdminIncidentsReadModel.SignalFilter filter,
      String tenantColumn,
      String environmentColumn,
      String roomColumn,
      String meetingColumn,
      String subjectColumn) {
    if (filter.tenantId() != null) {
      sql.append(" and ").append(tenantColumn).append(" = :tenantId");
    }
    if (filter.environmentType() != null) {
      sql.append(" and ").append(environmentColumn).append(" = :environmentType");
    }
    if (filter.roomId() != null) {
      sql.append(" and ").append(roomColumn).append(" = :roomId");
    }
    if (filter.meetingId() != null) {
      sql.append(" and ").append(meetingColumn).append(" = :meetingId");
    }
    if (filter.subjectId() != null) {
      sql.append(" and ").append(subjectColumn).append(" = :subjectId");
    }
  }

  void bindSharedFilters(Query query, AdminIncidentsReadModel.SignalFilter filter) {
    query.setParameter("from", Timestamp.from(filter.from()));
    query.setParameter("to", Timestamp.from(filter.to()));
    if (filter.tenantId() != null) {
      query.setParameter("tenantId", filter.tenantId());
    }
    if (filter.environmentType() != null) {
      query.setParameter("environmentType", filter.environmentType().name());
    }
    if (filter.roomId() != null) {
      query.setParameter("roomId", filter.roomId());
    }
    if (filter.meetingId() != null) {
      query.setParameter("meetingId", filter.meetingId());
    }
    if (filter.subjectId() != null) {
      query.setParameter("subjectId", filter.subjectId());
    }
    if (filter.errorCode() != null) {
      query.setParameter("errorCode", filter.errorCode().trim().toUpperCase(Locale.ROOT));
    }
  }

  void appendMeetingSignalFilters(StringBuilder sql, AdminIncidentsReadModel.SignalFilter filter) {
    if (filter.errorCode() != null) {
      sql.append(" and lower(audit.changed_fields) like :meetingErrorCodePattern");
    }
    if (filter.category() != null) {
      sql.append(" and lower(audit.changed_fields) like :meetingCategoryPattern");
    }
  }

  void bindMeetingSignalFilters(Query query, AdminIncidentsReadModel.SignalFilter filter) {
    if (filter.errorCode() != null) {
      query.setParameter(
          "meetingErrorCodePattern",
          "%errorcode=" + filter.errorCode().trim().toLowerCase(Locale.ROOT) + "%");
    }
    if (filter.category() != null) {
      query.setParameter(
          "meetingCategoryPattern",
          "%reasoncategory=" + filter.category().trim().toLowerCase(Locale.ROOT) + "%");
    }
  }

  void appendAuthCategoryFilter(StringBuilder sql, String category) {
    String normalizedCategory = normalizeCategoryToken(category);
    if (normalizedCategory == null) {
      return;
    }
    switch (normalizedCategory) {
      case "TOKEN" -> sql.append(" and upper(audit.error_code) like 'TOKEN_%'");
      case "SSO" -> sql.append(" and (upper(audit.error_code) = 'AUTH_REQUIRED' or upper(coalesce(audit.event_type, '')) = 'SSO_LOGIN_FAILED')");
      case "CONFIG" -> sql.append(" and upper(audit.error_code) = 'CONFIG_INCOMPATIBLE'");
      case "ROLE" -> sql.append(" and upper(audit.error_code) in ('ROLE_MISMATCH', 'ROLE_CONFLICT', 'MEETING_ROLE_CONFLICT')");
      case "NETWORK" -> sql.append(
          " and upper(audit.error_code) not like 'TOKEN_%'"
              + " and upper(audit.error_code) <> 'AUTH_REQUIRED'"
              + " and upper(coalesce(audit.event_type, '')) <> 'SSO_LOGIN_FAILED'"
              + " and upper(audit.error_code) <> 'CONFIG_INCOMPATIBLE'"
              + " and upper(audit.error_code) not in ('ROLE_MISMATCH', 'ROLE_CONFLICT', 'MEETING_ROLE_CONFLICT')");
      default -> sql.append(" and 1 = 0");
    }
  }

  void appendConfigFilters(StringBuilder sql, AdminIncidentsReadModel.SignalFilter filter) {
    if (filter.tenantId() != null) {
      sql.append(" and config.tenant_id = :tenantId");
    }
    if (filter.environmentType() != null) {
      sql.append(" and config.environment_type = :environmentType");
    }
    if (filter.category() != null) {
      String normalizedCategory = normalizeCategoryToken(filter.category());
      if (!"CONFIG".equals(normalizedCategory)) {
        sql.append(" and 1 = 0");
      }
    }
    if (filter.errorCode() != null) {
      sql.append(" and :errorCode = 'CONFIG_INCOMPATIBLE'");
    }
  }

  void bindConfigFilters(Query query, AdminIncidentsReadModel.SignalFilter filter) {
    query.setParameter("from", Timestamp.from(filter.from()));
    query.setParameter("to", Timestamp.from(filter.to()));
    if (filter.tenantId() != null) {
      query.setParameter("tenantId", filter.tenantId());
    }
    if (filter.environmentType() != null) {
      query.setParameter("environmentType", filter.environmentType().name());
    }
    if (filter.errorCode() != null) {
      query.setParameter("errorCode", filter.errorCode().trim().toUpperCase(Locale.ROOT));
    }
  }

  private String normalizeCategoryToken(String category) {
    return category == null || category.isBlank() ? null : category.trim().toUpperCase(Locale.ROOT);
  }
}