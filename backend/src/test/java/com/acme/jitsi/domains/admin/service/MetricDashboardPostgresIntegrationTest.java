package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.shared.observability.MetricCatalog;
import com.acme.jitsi.shared.observability.ServerMetricsService;
import com.acme.jitsi.support.PostgresRedisContainerIntegrationTestSupport;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

@Tag("container")
class MetricDashboardPostgresIntegrationTest extends PostgresRedisContainerIntegrationTestSupport {
  @Test
  void jsonbPreferencesRemainOwnedAndBothInsertAndUpdateRacesHaveOneWinner() throws Exception {
    var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    var jdbc = JdbcClient.create(dataSource);
    var metrics = mock(ServerMetricsService.class);
    when(metrics.catalog()).thenReturn(new MetricCatalog().descriptors());
    var service = new AdminMetricsService(jdbc, metrics, JsonMapper.builder().build());
    assertThat(service.loadDashboard("tenant-a", "admin-a").revision()).isZero();
    for (long revision : List.of(0L, 1L)) {
      var start = new CountDownLatch(1);
      var input = new AdminMetricsService.Dashboard(revision, "7d", List.of());
      try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
        var results = java.util.stream.IntStream.range(0, 2).mapToObj(i -> workers.submit(() -> {
          start.await();
          try { service.saveDashboard("tenant-a", "admin-a", input); return true; }
          catch (OptimisticLockingFailureException conflict) { return false; }
        })).toList();
        start.countDown();
        assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
      }
    }
    service.saveDashboard("tenant-b", "admin-a", new AdminMetricsService.Dashboard(0, "1h", List.of()));
    service.saveDashboard("tenant-a", "admin-b", new AdminMetricsService.Dashboard(0, "6h", List.of()));
    assertThat(service.loadDashboard("tenant-a", "admin-a").revision()).isEqualTo(2);
    assertThat(service.loadDashboard("tenant-a", "admin-a").widgets()).isEmpty();
    assertThat(service.loadDashboard("tenant-a", "admin-a").period()).isEqualTo("7d");
    assertThat(service.loadDashboard("tenant-b", "admin-a").revision()).isEqualTo(1);
    assertThat(service.loadDashboard("tenant-a", "admin-b").period()).isEqualTo("6h");
    assertThat(jdbc.sql("SELECT data_type FROM information_schema.columns WHERE table_name='metric_dashboard_preferences' AND column_name='layout'").query(String.class).single()).isEqualTo("jsonb");
    assertThat(jdbc.sql("SELECT count(*) FROM metric_dashboard_preferences").query(Integer.class).single()).isEqualTo(3);
  }
}
