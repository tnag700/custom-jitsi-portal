package com.acme.jitsi.domains.admin.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.admin.service.AdminDashboardReadModel;
import com.acme.jitsi.domains.configsets.service.ConfigSetEnvironmentType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminDashboardReadJpaAdapterTest {

  @Mock
  private EntityManager entityManager;

  @Mock
  private Query query;

  private AdminDashboardReadJpaAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new AdminDashboardReadJpaAdapter(entityManager);
    when(entityManager.createNativeQuery(anyString())).thenReturn(query);
    when(query.setParameter(anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(query);
    when(query.setMaxResults(org.mockito.ArgumentMatchers.anyInt())).thenReturn(query);
  }

  @Test
  void exactLimitDoesNotMarkSampleWindowAsLimited() {
    when(query.getResultList()).thenReturn(List.of(
        joinFailedRow("TOKEN_INVALID", "TOKEN", "trace-1"),
        joinFailedRow("CONFIG_INCOMPATIBLE", "CONFIG", "trace-2")));

    AdminDashboardReadModel.JoinAuditOverview overview = adapter.loadJoinAuditOverview(
        new AdminDashboardReadModel.DashboardFilter(
            "tenant-1",
            ConfigSetEnvironmentType.DEV,
            Instant.parse("2026-03-18T09:00:00Z"),
            null,
            null,
            2));

    assertThat(overview.sampleWindowLimited()).isFalse();
  }

  @Test
  void topCategoriesIgnoreUnsupportedReasonCategories() {
    when(query.getResultList()).thenReturn(List.of(
        joinFailedRow("TOKEN_INVALID", "TOKEN", "trace-1"),
        joinFailedRow("CONFIG_INCOMPATIBLE", "custom-category", "trace-2"),
        joinFailedRow("ACCESS_DENIED", "SSO", "trace-3")));

    AdminDashboardReadModel.JoinAuditOverview overview = adapter.loadJoinAuditOverview(
        new AdminDashboardReadModel.DashboardFilter(
            "tenant-1",
            ConfigSetEnvironmentType.DEV,
            Instant.parse("2026-03-18T09:00:00Z"),
            null,
            null,
            10));

    assertThat(overview.topCategories())
        .extracting(AdminDashboardReadModel.Count::key)
        .containsExactly("SSO", "TOKEN");
  }

  private Object[] joinFailedRow(String errorCode, String reasonCategory, String traceId) {
    return new Object[] {
        Timestamp.from(Instant.parse("2026-03-18T09:55:00Z")),
        "room-1",
        "meeting-1",
        "subject-1",
        traceId,
        "join_failed",
        "errorCode=%s,reasonCategory=%s".formatted(errorCode, reasonCategory)
    };
  }
}