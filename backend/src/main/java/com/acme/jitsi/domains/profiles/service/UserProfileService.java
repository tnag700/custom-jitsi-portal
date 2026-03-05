package com.acme.jitsi.domains.profiles.service;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

  private final UserProfileRepository repository;

  public UserProfileService(UserProfileRepository repository) {
    this.repository = repository;
  }

  public UserProfile getBySubjectId(String subjectId) {
    return repository.findBySubjectId(subjectId)
        .orElseThrow(() -> new ProfileNotFoundException(subjectId));
  }

  public List<UserProfile> searchUsers(String tenantId, String query, String organization, int limit) {
    return repository.searchByTenantId(tenantId, query, organization, limit);
  }

  public List<UserProfile> findBySubjectIds(List<String> subjectIds) {
    return repository.findBySubjectIds(subjectIds);
  }
}
