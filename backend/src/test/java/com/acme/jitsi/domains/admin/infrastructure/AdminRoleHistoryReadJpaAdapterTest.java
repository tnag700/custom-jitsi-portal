package com.acme.jitsi.domains.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.admin.service.AdminRoleHistoryReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRoleHistoryReadJpaAdapterTest {

  @Mock
  private EntityManager entityManager;

  @Mock
  private Query countQuery;

  @Mock
  private Query dataQuery;

  private AdminRoleHistoryReadJpaAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AdminRoleHistoryReadJpaAdapter(entityManager);
    when(entityManager.createNativeQuery(anyString())).thenReturn(countQuery, dataQuery);
    when(countQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(countQuery);
    when(dataQuery.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(dataQuery);
    when(dataQuery.setFirstResult(org.mockito.ArgumentMatchers.anyInt())).thenReturn(dataQuery);
    when(dataQuery.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(dataQuery);
  }

  @SuppressWarnings("rawtypes")
  @Test
  void parsesOldAndNewRolesFromAuditPayloadAndReturnsTotalCount() {
    when(countQuery.getSingleResult()).thenReturn(1L);
    List<Object[]> rows = new ArrayList<>();
    rows.add(new Object[] {
      17L,
      Timestamp.from(Instant.parse("2026-03-19T09:55:00Z")),
      "tenant-1",
      "DEV",
      "room-1",
      "meeting-1",
      "admin-1",
      "user-1",
      "trace-1",
      "update",
      "subjectId:user-1;role:participant->moderator",
      "Иван Иванов",
      "Мария Петрова"
    });
    when(dataQuery.getResultList()).thenReturn((List) rows);

    var result = adapter.loadHistory(new AdminRoleHistoryReadModel.Filter(
        "tenant-1",
        ConfigSetEnvironmentType.DEV,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Instant.parse("2026-03-18T00:00:00Z"),
        Instant.parse("2026-03-19T10:00:00Z"),
        0,
        20));

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).oldRole()).isEqualTo("participant");
    assertThat(result.rows().get(0).newRole()).isEqualTo("moderator");
    assertThat(result.rows().get(0).subjectFullName()).isEqualTo("Иван Иванов");
  }
}