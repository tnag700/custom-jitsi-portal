package com.acme.jitsi.domains.profiles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.profiles.event.UserProfileAdminUpdatedEvent;
import com.acme.jitsi.domains.profiles.event.UserProfileUpdatedEvent;
import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class UpdateUserProfileUseCaseTest {

  private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-07-30T12:00:00Z");

  @Mock
  private UserProfileRepository repository;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private UpdateUserProfileUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new UpdateUserProfileUseCase(
        repository,
        eventPublisher,
        Clock.fixed(UPDATED_AT, ZoneOffset.UTC));
  }

  @Test
  void updatesOnlyProfileFromAdministratorsTenantAndPublishesAuditEvent() {
    UserProfile existing = profile("tenant-1");
    when(repository.findBySubjectIdAndTenantId("user-1", "tenant-1"))
        .thenReturn(Optional.of(existing));
    when(repository.save(any(UserProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UserProfile result = useCase.execute(command("tenant-1"));

    assertThat(result.fullName()).isEqualTo("Новое Имя");
    assertThat(result.organization()).isEqualTo("Новая организация");
    assertThat(result.position()).isEqualTo("Новая должность");
    assertThat(result.tenantId()).isEqualTo("tenant-1");
    assertThat(result.updatedAt()).isEqualTo(UPDATED_AT);
    verify(eventPublisher).publishEvent(any(UserProfileUpdatedEvent.class));

    ArgumentCaptor<UserProfileAdminUpdatedEvent> auditCaptor =
        ArgumentCaptor.forClass(UserProfileAdminUpdatedEvent.class);
    verify(eventPublisher).publishEvent(auditCaptor.capture());
    assertThat(auditCaptor.getValue().actorId()).isEqualTo("admin-1");
    assertThat(auditCaptor.getValue().changedFields())
        .containsExactly("fullName", "organization", "position");
  }

  @Test
  void rejectsProfileFromAnotherTenantWithoutSaving() {
    when(repository.findBySubjectIdAndTenantId("user-1", "tenant-1"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute(command("tenant-1")))
        .isInstanceOf(ProfileNotFoundException.class);

    verify(repository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void unchangedProfileIsNotWrittenOrAudited() {
    UserProfile existing = profile("tenant-1");
    when(repository.findBySubjectIdAndTenantId("user-1", "tenant-1"))
        .thenReturn(Optional.of(existing));
    UpdateUserProfileCommand unchanged = new UpdateUserProfileCommand(
        "admin-1",
        "user-1",
        "tenant-1",
        existing.fullName(),
        existing.organization(),
        existing.position(),
        "trace-1");

    assertThat(useCase.execute(unchanged)).isSameAs(existing);
    verify(repository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  private UserProfile profile(String tenantId) {
    return new UserProfile(
        "profile-1",
        "user-1",
        tenantId,
        "Старое Имя",
        "Старая организация",
        "Старая должность",
        CREATED_AT,
        CREATED_AT);
  }

  private UpdateUserProfileCommand command(String tenantId) {
    return new UpdateUserProfileCommand(
        "admin-1",
        "user-1",
        tenantId,
        "Новое Имя",
        "Новая организация",
        "Новая должность",
        "trace-1");
  }
}
