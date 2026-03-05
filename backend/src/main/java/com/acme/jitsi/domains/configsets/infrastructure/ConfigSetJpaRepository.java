package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import com.acme.jitsi.domains.configsets.service.ConfigSetStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConfigSetJpaRepository extends JpaRepository<ConfigSetEntity, String> {

  Optional<ConfigSetEntity> findByTenantIdAndEnvironmentTypeAndStatus(
      String tenantId,
      ConfigSetEnvironmentType environmentType,
      ConfigSetStatus status);

  List<ConfigSetEntity> findByStatus(ConfigSetStatus status);

  Page<ConfigSetEntity> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

  long countByTenantId(String tenantId);

  boolean existsByNameAndTenantId(String name, String tenantId);

  boolean existsByNameAndTenantIdAndConfigSetIdNot(String name, String tenantId, String configSetId);
}