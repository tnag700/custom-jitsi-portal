package com.acme.jitsi.domains.profiles.application;

import com.acme.jitsi.domains.profiles.event.UserProfileAdminUpdatedEvent;
import com.acme.jitsi.domains.profiles.event.UserProfileUpdatedEvent;
import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.ProfileValidationException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import com.acme.jitsi.infrastructure.usecase.UseCase;
import com.acme.jitsi.shared.validation.TextInputNormalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateUserProfileUseCase implements UseCase<UpdateUserProfileCommand, UserProfile> {

  private final UserProfileRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  public UpdateUserProfileUseCase(
      UserProfileRepository repository,
      ApplicationEventPublisher eventPublisher,
      Clock clock) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.clock = clock;
  }

  @Override
  @Transactional
  public UserProfile execute(UpdateUserProfileCommand command) {
    validateCommand(command);
    UserProfile existing = repository
        .findBySubjectIdAndTenantId(command.subjectId(), command.tenantId())
        .orElseThrow(() -> new ProfileNotFoundException(command.subjectId()));

    List<String> changedFields = changedFields(existing, command);
    if (changedFields.isEmpty()) {
      return existing;
    }

    UserProfile updated = new UserProfile(
        existing.id(),
        existing.subjectId(),
        existing.tenantId(),
        command.fullName(),
        command.organization(),
        command.position(),
        existing.createdAt(),
        Instant.now(clock));
    UserProfile saved = repository.save(updated);

    eventPublisher.publishEvent(new UserProfileUpdatedEvent(
        saved.id(), saved.subjectId(), saved.tenantId(), saved.fullName()));
    eventPublisher.publishEvent(new UserProfileAdminUpdatedEvent(
        saved.id(),
        saved.subjectId(),
        saved.tenantId(),
        command.actorId(),
        command.traceId(),
        List.copyOf(changedFields)));
    return saved;
  }

  private void validateCommand(UpdateUserProfileCommand command) {
    validateRequiredField("actorId", command.actorId(), 1, 255);
    validateRequiredField("subjectId", command.subjectId(), 1, 255);
    validateRequiredField("tenantId", command.tenantId(), 1, 255);
    validateRequiredField("fullName", command.fullName(), 2, 500);
    validateRequiredField("organization", command.organization(), 2, 500);
    validateRequiredField("position", command.position(), 2, 500);
    validateRequiredField("traceId", command.traceId(), 1, 255);
  }

  private void validateRequiredField(String fieldName, String value, int minLength, int maxLength) {
    if (value == null) {
      throw new ProfileValidationException(fieldName + " is required");
    }
    String normalized = TextInputNormalizer.normalizeRequired(value);
    if (normalized.length() < minLength || normalized.length() > maxLength) {
      throw new ProfileValidationException(
          fieldName + " must be between " + minLength + " and " + maxLength + " characters");
    }
  }

  private List<String> changedFields(UserProfile existing, UpdateUserProfileCommand command) {
    List<String> changed = new ArrayList<>();
    if (!existing.fullName().equals(command.fullName())) {
      changed.add("fullName");
    }
    if (!existing.organization().equals(command.organization())) {
      changed.add("organization");
    }
    if (!existing.position().equals(command.position())) {
      changed.add("position");
    }
    return changed;
  }
}
