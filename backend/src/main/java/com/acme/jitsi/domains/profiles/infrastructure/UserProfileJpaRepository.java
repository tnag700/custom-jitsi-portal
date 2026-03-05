package com.acme.jitsi.domains.profiles.infrastructure;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserProfileJpaRepository extends JpaRepository<UserProfileEntity, String> {

  Optional<UserProfileEntity> findBySubjectId(String subjectId);

  @Query("""
      SELECT p FROM UserProfileEntity p
      WHERE p.tenantId = :tenantId
      AND (:query IS NULL OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :query, '%')))
      AND (:organization IS NULL OR p.organization = :organization)
      ORDER BY p.fullName ASC
      LIMIT :limit
      """)
  List<UserProfileEntity> searchByTenantId(
      @Param("tenantId") String tenantId,
      @Param("query") String query,
      @Param("organization") String organization,
      @Param("limit") int limit);

  List<UserProfileEntity> findBySubjectIdIn(List<String> subjectIds);
}
