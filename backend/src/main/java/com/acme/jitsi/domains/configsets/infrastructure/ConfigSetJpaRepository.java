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

  Page<ConfigSetEntity> findByTenantIdAndEnvironmentTypeOrderByCreatedAtDesc(
      String tenantId, ConfigSetEnvironmentType environmentType, Pageable pageable);

  Page<ConfigSetEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(
      String tenantId, ConfigSetStatus status, Pageable pageable);

  Page<ConfigSetEntity> findByTenantIdAndEnvironmentTypeAndStatusOrderByCreatedAtDesc(
      String tenantId, ConfigSetEnvironmentType environmentType, ConfigSetStatus status,
      Pageable pageable);

  boolean existsByNameAndTenantId(String name, String tenantId);

  boolean existsByNameAndTenantIdAndConfigSetIdNot(String name, String tenantId, String configSetId);
}
