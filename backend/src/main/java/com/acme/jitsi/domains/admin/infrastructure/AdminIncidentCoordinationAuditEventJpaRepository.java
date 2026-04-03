package com.acme.jitsi.domains.admin.infrastructure;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminIncidentCoordinationAuditEventJpaRepository extends JpaRepository<AdminIncidentCoordinationAuditEventEntity, Long> {

  List<AdminIncidentCoordinationAuditEventEntity> findTop5ByIncidentIdAndTenantIdAndEnvironmentOrderByCreatedAtDescIdDesc(
      String incidentId,
      String tenantId,
      String environment);
}