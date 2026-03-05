package com.acme.jitsi.domains.profiles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.profiles.service.UserProfile;
import com.acme.jitsi.domains.profiles.service.UserProfileRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchUsersUseCaseTest {

  @Mock
  private UserProfileRepository repository;

  private SearchUsersUseCase useCase;

  @BeforeEach
  void setUp() {
    useCase = new SearchUsersUseCase(repository);
  }

  @Test
  void executeReturnsMatchingUsers() {
    UserProfile p1 = new UserProfile(
        "id-1", "sub-1", "tenant-1", "Иванов", "МВД", "Оперативник",
        Instant.now(), Instant.now());
    UserProfile p2 = new UserProfile(
        "id-2", "sub-2", "tenant-1", "Иванова", "МВД", "Аналитик",
        Instant.now(), Instant.now());
    when(repository.searchByTenantId("tenant-1", "Иван", null, 50))
        .thenReturn(List.of(p1, p2));

    List<UserProfile> result = useCase.execute(new SearchUsersQuery("tenant-1", "Иван", null));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).fullName()).isEqualTo("Иванов");
  }

  @Test
  void executeReturnsEmptyListWhenNoMatches() {
    when(repository.searchByTenantId("tenant-1", "xyz", null, 50))
        .thenReturn(List.of());

    List<UserProfile> result = useCase.execute(new SearchUsersQuery("tenant-1", "xyz", null));

    assertThat(result).isEmpty();
  }

  @Test
  void executeFiltersbyOrganization() {
    UserProfile p1 = new UserProfile(
        "id-1", "sub-1", "tenant-1", "Петров", "ФГБУ", "Инженер",
        Instant.now(), Instant.now());
    when(repository.searchByTenantId("tenant-1", null, "ФГБУ", 50))
        .thenReturn(List.of(p1));

    List<UserProfile> result = useCase.execute(new SearchUsersQuery("tenant-1", null, "ФГБУ"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).organization()).isEqualTo("ФГБУ");
  }
}
