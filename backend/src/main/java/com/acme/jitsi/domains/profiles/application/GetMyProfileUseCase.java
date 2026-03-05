package com.acme.jitsi.domains.profiles.application;

import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import org.springframework.stereotype.Service;

@Service
public class GetMyProfileUseCase implements UseCase<String, UserProfile> {

  private final UserProfileRepository repository;

  public GetMyProfileUseCase(UserProfileRepository repository) {
    this.repository = repository;
  }

  @Override
  public UserProfile execute(String subjectId) {
    return repository.findBySubjectId(subjectId)
        .orElseThrow(() -> new ProfileNotFoundException(subjectId));
  }
}
