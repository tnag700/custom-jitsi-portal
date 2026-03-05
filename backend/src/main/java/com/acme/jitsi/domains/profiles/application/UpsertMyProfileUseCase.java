package com.acme.jitsi.domains.profiles.application;

import com.acme.jitsi.domains.profiles.event.UserProfileCreatedEvent;
import com.acme.jitsi.domains.profiles.event.UserProfileUpdatedEvent;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.ProfileValidationException;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpsertMyProfileUseCase implements UseCase<UpsertProfileCommand, UserProfile> {

  private final UserProfileRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public UpsertMyProfileUseCase(
      UserProfileRepository repository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public UserProfile execute(UpsertProfileCommand command) {
    validateCommand(command);

    Instant now = Instant.now(clock);
    boolean isNew = repository.findBySubjectId(command.subjectId()).isEmpty();

    UserProfile profile;
    if (isNew) {
      profile = new UserProfile(
          UUID.randomUUID().toString(),
          command.subjectId(),
          command.tenantId(),
          command.fullName(),
          command.organization(),
          command.position(),
          now,
          now);
    } else {
      UserProfile existing = repository.findBySubjectId(command.subjectId()).orElseThrow();
      profile = new UserProfile(
          existing.id(),
          existing.subjectId(),
          existing.tenantId(),
          command.fullName(),
          command.organization(),
          command.position(),
          existing.createdAt(),
          now);
    }

    UserProfile saved = repository.save(profile);

    if (isNew) {
      eventPublisher.publishEvent(new UserProfileCreatedEvent(
          saved.id(), saved.subjectId(), saved.tenantId(), saved.fullName()));
    } else {
      eventPublisher.publishEvent(new UserProfileUpdatedEvent(
          saved.id(), saved.subjectId(), saved.tenantId(), saved.fullName()));
    }

    return saved;
  }

  private void validateCommand(UpsertProfileCommand command) {
    validateRequiredField("subjectId", command.subjectId(), 1, 255);
    validateRequiredField("tenantId", command.tenantId(), 1, 255);
    validateRequiredField("fullName", command.fullName(), 2, 500);
    validateRequiredField("organization", command.organization(), 2, 500);
    validateRequiredField("position", command.position(), 2, 500);
  }

  private void validateRequiredField(String fieldName, String value, int minLength, int maxLength) {
    if (value == null) {
      throw new ProfileValidationException(fieldName + " is required");
    }

    String normalized = value.trim();
    if (normalized.length() < minLength || normalized.length() > maxLength) {
      throw new ProfileValidationException(
          fieldName + " must be between " + minLength + " and " + maxLength + " characters");
    }
  }
}
