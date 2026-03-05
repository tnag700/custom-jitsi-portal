package com.acme.jitsi.domains.profiles.service;

import java.util.List;
import java.util.Optional;

public interface UserProfileRepository {
  UserProfile save(UserProfile profile);
  Optional<UserProfile> findBySubjectId(String subjectId);
  List<UserProfile> searchByTenantId(String tenantId, String query, String organization, int limit);
  List<UserProfile> findBySubjectIds(List<String> subjectIds);
}
