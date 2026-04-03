package com.acme.jitsi.domains.admin.infrastructure;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface AdminIncidentCoordinationStateJpaRepository extends JpaRepository<AdminIncidentCoordinationStateEntity, Long> {

  Optional<AdminIncidentCoordinationStateEntity> findByIncidentIdAndTenantIdAndEnvironment(
      String incidentId,
      String tenantId,
      String environment);
}