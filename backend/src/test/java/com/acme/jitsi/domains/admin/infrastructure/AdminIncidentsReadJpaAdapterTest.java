package com.acme.jitsi.domains.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.admin.service.AdminIncidentsReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminIncidentsReadJpaAdapterTest {

  @Mock
  private EntityManager entityManager;

  @Mock
  private Query query;

  private AdminIncidentsReadJpaAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AdminIncidentsReadJpaAdapter(entityManager);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
    when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(query);
    when(query.getResultList()).thenReturn(List.of());
  }

  @Test
  void appliesMeetingAndAuthFiltersForErrorCodeAndCategoryQueries() {
    adapter.loadSignals(new AdminIncidentsReadModel.SignalFilter(
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        Instant.parse("2026-03-18T09:00:00Z"),
        Instant.parse("2026-03-18T10:00:00Z"),
        null,
        null,
        null,
        "TOKEN_INVALID",
        "TOKEN",
        50));

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(entityManager, times(3)).createNativeQuery(sqlCaptor.capture());
    List<String> sqlStatements = sqlCaptor.getAllValues();

    assertThat(sqlStatements.get(0))
        .contains("lower(audit.changed_fields) like :meetingErrorCodePattern")
        .contains("lower(audit.changed_fields) like :meetingCategoryPattern");
    assertThat(sqlStatements.get(1))
        .contains("upper(audit.error_code) = :errorCode")
        .contains("upper(audit.error_code) like 'TOKEN_%'");
    assertThat(sqlStatements.get(2)).contains("and 1 = 0");
  }

  @Test
  void excludesNonConfigCategoryFromConfigSignals() {
    adapter.loadSignals(new AdminIncidentsReadModel.SignalFilter(
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        Instant.parse("2026-03-18T09:00:00Z"),
        Instant.parse("2026-03-18T10:00:00Z"),
        null,
        null,
        null,
        null,
        "ROLE",
        50));

    ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
    verify(entityManager, times(3)).createNativeQuery(sqlCaptor.capture());
    List<String> sqlStatements = sqlCaptor.getAllValues();

    assertThat(sqlStatements.get(2)).contains("and 1 = 0");
  }
}