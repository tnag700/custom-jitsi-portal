package com.acme.jitsi.domains.profiles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.profiles.service.ProfileNotFoundException;
import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetMyProfileUseCaseTest {

  @Mock
  private UserProfileRepository repository;

  private GetMyProfileUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new GetMyProfileUseCase(repository);
  }

  @Test
  void executeReturnsProfileWhenExists() {
    UserProfile profile = new UserProfile(
        "id-1", "sub-1", "tenant-1", "Иванов Иван", "Рога и Копыта", "Директор",
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
    when(repository.findBySubjectId("sub-1")).thenReturn(Optional.of(profile));

    UserProfile result = useCase.execute("sub-1");

    assertThat(result.subjectId()).isEqualTo("sub-1");
    assertThat(result.fullName()).isEqualTo("Иванов Иван");
    assertThat(result.organization()).isEqualTo("Рога и Копыта");
    assertThat(result.position()).isEqualTo("Директор");
  }

  @Test
  void executeThrowsProfileNotFoundWhenMissing() {
    when(repository.findBySubjectId("unknown")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> useCase.execute("unknown"))
        .isInstanceOf(ProfileNotFoundException.class)
        .hasMessageContaining("unknown");
  }
}
