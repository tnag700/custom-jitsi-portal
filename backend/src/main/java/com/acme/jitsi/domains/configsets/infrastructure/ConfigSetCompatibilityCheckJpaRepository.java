package com.acme.jitsi.domains.configsets.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ConfigSetCompatibilityCheckJpaRepository extends JpaRepository<ConfigSetCompatibilityCheckEntity, String> {

  Optional<ConfigSetCompatibilityCheckEntity> findTopByConfigSetIdOrderByCheckedAtDesc(String configSetId);

  @Query("""
      select c from ConfigSetCompatibilityCheckEntity c
      where c.configSetId in :configSetIds
        and c.checkedAt = (
          select max(c2.checkedAt)
          from ConfigSetCompatibilityCheckEntity c2
          where c2.configSetId = c.configSetId
        )
      """)
  List<ConfigSetCompatibilityCheckEntity> findLatestByConfigSetIds(
      @Param("configSetIds") List<String> configSetIds);
}