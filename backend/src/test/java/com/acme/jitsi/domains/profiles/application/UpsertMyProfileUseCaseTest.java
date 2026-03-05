package com.acme.jitsi.domains.profiles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.profiles.event.UserProfileCreatedEvent;
import com.acme.jitsi.domains.profiles.event.UserProfileUpdatedEvent;
import com.acme.jitsi.domains.profiles.service.ProfileValidationException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UpsertMyProfileUseCaseTest {

  @Mock
  private UserProfileRepository repository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private UpsertMyProfileUseCase useCase;

  private static final Instant FIXED_NOW = Instant.parse("2026-02-01T12:00:00Z");

  @BeforeEach
  void setUp() {
    useCase = new UpsertMyProfileUseCase(
        repository,
        eventPublisher,
        Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
  }

  @Test
  void executeCreatesNewProfileAndPublishesCreatedEvent() {
    when(repository.findBySubjectId("sub-1")).thenReturn(Optional.empty());
    when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

    UpsertProfileCommand command = new UpsertProfileCommand(
        "sub-1", "tenant-1", "Петров Пётр", "ФГБУ НИИ", "Инженер");

    UserProfile result = useCase.execute(command);

    assertThat(result.subjectId()).isEqualTo("sub-1");
    assertThat(result.tenantId()).isEqualTo("tenant-1");
    assertThat(result.fullName()).isEqualTo("Петров Пётр");
    assertThat(result.organization()).isEqualTo("ФГБУ НИИ");
    assertThat(result.position()).isEqualTo("Инженер");
    assertThat(result.createdAt()).isEqualTo(FIXED_NOW);
    assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);

    verify(eventPublisher).publishEvent(any(UserProfileCreatedEvent.class));
  }

  @Test
  void executeUpdatesExistingProfileAndPublishesUpdatedEvent() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    UserProfile existing = new UserProfile(
        "id-1", "sub-1", "tenant-1", "Старое Имя", "Старая Орг", "Старая Должность",
        createdAt, createdAt);
    when(repository.findBySubjectId("sub-1")).thenReturn(Optional.of(existing));
    when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

    UpsertProfileCommand command = new UpsertProfileCommand(
        "sub-1", "tenant-1", "Новое Имя", "Новая Орг", "Новая Должность");

    UserProfile result = useCase.execute(command);

    assertThat(result.id()).isEqualTo("id-1");
    assertThat(result.fullName()).isEqualTo("Новое Имя");
    assertThat(result.organization()).isEqualTo("Новая Орг");
    assertThat(result.position()).isEqualTo("Новая Должность");
    assertThat(result.createdAt()).isEqualTo(createdAt);
    assertThat(result.updatedAt()).isEqualTo(FIXED_NOW);

    verify(eventPublisher).publishEvent(any(UserProfileUpdatedEvent.class));
  }

  @Test
  void executePreservesOriginalTenantIdOnUpdate() {
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    UserProfile existing = new UserProfile(
        "id-1", "sub-1", "original-tenant", "Имя", "Орг", "Должность",
        createdAt, createdAt);
    when(repository.findBySubjectId("sub-1")).thenReturn(Optional.of(existing));
    when(repository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

    UpsertProfileCommand command = new UpsertProfileCommand(
        "sub-1", "different-tenant", "Новое Имя", "Новая Орг", "Новая Должность");

    UserProfile result = useCase.execute(command);

    assertThat(result.tenantId()).isEqualTo("original-tenant");
  }

  @Test
  void executeThrowsValidationExceptionWhenFullNameBlank() {
    UpsertProfileCommand command = new UpsertProfileCommand(
        "sub-1", "tenant-1", " ", "Org", "Position");

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ProfileValidationException.class)
        .hasMessageContaining("fullName");

    verify(repository, never()).save(any(UserProfile.class));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void executeThrowsValidationExceptionWhenPositionTooShort() {
    UpsertProfileCommand command = new UpsertProfileCommand(
        "sub-1", "tenant-1", "Иванов Иван", "Организация", "X");

    assertThatThrownBy(() -> useCase.execute(command))
        .isInstanceOf(ProfileValidationException.class)
        .hasMessageContaining("position");

    verify(repository, never()).save(any(UserProfile.class));
    verify(eventPublisher, never()).publishEvent(any());
  }
}
