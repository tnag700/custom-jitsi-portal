package com.acme.jitsi.domains.profiles.infrastructure;

import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaUserProfileRepository implements UserProfileRepository {

  private final UserProfileJpaRepository jpaRepository;

  JpaUserProfileRepository(UserProfileJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public UserProfile save(UserProfile profile) {
    UserProfileEntity entity = jpaRepository.findBySubjectId(profile.subjectId())
        .map(existing -> {
          existing.updateFrom(profile);
          return existing;
        })
        .orElseGet(() -> new UserProfileEntity(profile));

    UserProfileEntity saved = jpaRepository.save(entity);
    return saved.toDomain();
  }

  @Override
  public Optional<UserProfile> findBySubjectId(String subjectId) {
    return jpaRepository.findBySubjectId(subjectId)
        .map(UserProfileEntity::toDomain);
  }

  @Override
  public List<UserProfile> searchByTenantId(String tenantId, String query, String organization, int limit) {
    return jpaRepository.searchByTenantId(tenantId, query, organization, limit).stream()
        .map(UserProfileEntity::toDomain)
        .toList();
  }

  @Override
  public List<UserProfile> findBySubjectIds(List<String> subjectIds) {
    if (subjectIds.isEmpty()) {
      return List.of();
    }
    return jpaRepository.findBySubjectIdIn(subjectIds).stream()
        .map(UserProfileEntity::toDomain)
        .toList();
  }
}
