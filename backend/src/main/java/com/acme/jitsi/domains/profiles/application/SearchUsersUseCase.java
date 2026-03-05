package com.acme.jitsi.domains.profiles.application;

import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchUsersUseCase implements UseCase<SearchUsersQuery, List<UserProfile>> {

  private static final int MAX_RESULTS = 50;

  private final UserProfileRepository repository;

  public SearchUsersUseCase(UserProfileRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<UserProfile> execute(SearchUsersQuery query) {
    return repository.searchByTenantId(
        query.tenantId(),
        query.query(),
        query.organization(),
        MAX_RESULTS);
  }
}
