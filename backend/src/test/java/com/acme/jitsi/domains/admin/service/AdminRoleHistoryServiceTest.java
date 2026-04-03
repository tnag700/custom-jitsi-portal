package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRoleHistoryServiceTest {

  @Mock
  private AdminRoleHistoryReadModel readModel;

  private AdminRoleHistoryService service;

  @BeforeEach
  void setUp() {
    service = new AdminRoleHistoryService(
        readModel,
        Clock.fixed(Instant.parse("2026-03-19T10:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void masksSensitiveReferencesForSupportEngineerAndMapsTimeline() {
    when(readModel.loadHistory(any()))
        .thenReturn(new AdminRoleHistoryReadModel.PageResult(
            List.of(new AdminRoleHistoryReadModel.RoleHistoryRow(
                Instant.parse("2026-03-19T09:55:00Z"),
                "update",
                "participant",
                "moderator",
                "tenant-1",
                ConfigSetEnvironmentType.DEV,
                "room-1",
                "meeting-1",
                "admin-user-01",
                "Мария Петрова",
                "user-sensitive-45",
                "Иван Иванов",
                "trace-1")),
            1));

    var response = service.getRoleHistory(
        "tenant-1",
        List.of("ROLE_support-engineer"),
        new AdminRoleHistoryService.AdminRoleHistoryQuery(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            "meeting-1",
            null,
            0,
            20));

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().get(0).subjectLabel()).isEqualTo("Иван Иванов");
    assertThat(response.content().get(0).subjectReference()).contains("***");
    assertThat(response.content().get(0).actorReference()).contains("***");
    assertThat(response.content().get(0).actionLabel()).isEqualTo("Изменение роли");
  }

  @Test
  void passesBoundedFiltersToReadModel() {
    when(readModel.loadHistory(any()))
        .thenReturn(new AdminRoleHistoryReadModel.PageResult(List.of(), 0));

    service.getRoleHistory(
        "tenant-1",
        List.of("ROLE_security-admin"),
        new AdminRoleHistoryService.AdminRoleHistoryQuery(
            "dev",
            "иван",
            "2026-03-18T00:00:00Z",
            "2026-03-19T00:00:00Z",
            "update",
            "moderator",
            "actor-1",
            "subject-1",
            "room-1",
            "meeting-1",
            null,
            2,
            500));

    ArgumentCaptor<AdminRoleHistoryReadModel.Filter> captor = ArgumentCaptor.forClass(AdminRoleHistoryReadModel.Filter.class);
    verify(readModel).loadHistory(captor.capture());
    assertThat(captor.getValue().environmentType()).isEqualTo(ConfigSetEnvironmentType.DEV);
    assertThat(captor.getValue().query()).isEqualTo("иван");
    assertThat(captor.getValue().page()).isEqualTo(2);
    assertThat(captor.getValue().pageSize()).isEqualTo(100);
  }
}