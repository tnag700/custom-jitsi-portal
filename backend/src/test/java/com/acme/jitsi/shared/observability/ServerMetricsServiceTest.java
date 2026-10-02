package com.acme.jitsi.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ServerMetricsServiceTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final Instant now = Instant.parse("2026-10-02T09:00:00Z");

  @Test
  void summaryIsExplicitRoundedProjectionAndConcurrentRefreshIsShared() throws Exception {
    var calls = new AtomicInteger();
    var client = source(calls, 0, false);
    var service = new ServerMetricsService(client, new MetricCatalog(), Clock.fixed(now, ZoneOffset.UTC));
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      var results = java.util.stream.IntStream.range(0, 20)
          .mapToObj(i -> workers.submit(service::summary)).toList();
      for (var result : results) {
        var summary = result.get();
        assertThat(summary.cpuPercent()).isEqualTo(65);
        assertThat(summary.memoryPercent()).isEqualTo(40);
        assertThat(summary.diskState()).isEqualTo("low");
        assertThat(summary.backendState()).isEqualTo("working");
        assertThat(mapper.writeValueAsString(summary)).doesNotContain("instance", "query", "bytes", "internal-host");
      }
    }
    assertThat(calls).hasValue(22); // two bounded queries per definition, plus traffic denominators
  }

  @Test
  void originalSamplesAndTargetAvailabilityControlFreshness() {
    var stale = new ServerMetricsService(source(new AtomicInteger(), 91, false), new MetricCatalog(), Clock.fixed(now, ZoneOffset.UTC));
    assertThat(stale.summary().cpuPercent()).isNull();
    assertThat(stale.summary().stale()).isTrue();
    var down = new ServerMetricsService(source(new AtomicInteger(), 0, true), new MetricCatalog(), Clock.fixed(now, ZoneOffset.UTC));
    assertThat(down.summary().cpuPercent()).isNull();
    assertThat(down.summary().backendState()).isEqualTo("problem");
  }

  @Test
  void invalidSelectionsNeverReachSourceAndRangeHasBoundedGaps() {
    var client = source(new AtomicInteger(), 0, false);
    var service = new ServerMetricsService(client, new MetricCatalog(), Clock.fixed(now, ZoneOffset.UTC));
    assertThatThrownBy(() -> service.query(List.of("host.cpu", "host.cpu"), ServerMetricsService.MetricPeriod.HOUR)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.query(List.of("untrusted.promql"), ServerMetricsService.MetricPeriod.HOUR)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ServerMetricsService.MetricPeriod.parse("forever")).isInstanceOf(IllegalArgumentException.class);
    when(client.queryRange(anyString(), any(), any(), anyLong())).thenReturn(CompletableFuture.completedFuture(mapper.readTree("{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[{\"metric\":{\"instance\":\"internal-host\"},\"values\":[[1790931600,\"NaN\"]]}]}}")));
    var result = service.query(List.of("host.cpu"), ServerMetricsService.MetricPeriod.WEEK);
    assertThat(result.metrics().getFirst().series().getFirst().points()).hasSizeLessThanOrEqualTo(300);
    assertThat(result.metrics().getFirst().series().getFirst().points()).allMatch(point -> point.value() == null);
    assertThat(mapper.writeValueAsString(result)).doesNotContain("internal-host", "instance", "NaN");
    service.query(List.of("host.cpu"), ServerMetricsService.MetricPeriod.WEEK);
    verify(client, org.mockito.Mockito.times(1)).queryRange(anyString(), any(), any(), anyLong());
  }

  @Test
  void emptyNonFiniteExtraRowsAndNoTrafficAreNotHealthyZero() {
    var client = source(new AtomicInteger(), 0, false);
    when(client.queryInstant(anyString(), any())).thenAnswer(invocation -> {
      String query = invocation.getArgument(0);
      if (query.contains("timestamp(")) return CompletableFuture.completedFuture(vector(now.getEpochSecond()));
      if (query.contains("sum(rate(jitsi_join_attempts_total") && !query.startsWith("100")) return CompletableFuture.completedFuture(vector(0));
      return CompletableFuture.completedFuture(vector(Double.NaN));
    });
    var service = new ServerMetricsService(client, new MetricCatalog(), Clock.fixed(now, ZoneOffset.UTC));
    assertThat(service.query(List.of("jwt.error-ratio"), ServerMetricsService.MetricPeriod.HOUR).metrics().getFirst().state()).isEqualTo("no_traffic");
    assertThat(service.summary().cpuPercent()).isNull();
    assertThat(ServerMetricsService.scalar(mapper.readTree("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}"))).isNull();
    assertThat(ServerMetricsService.scalar(mapper.readTree("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"value\":[1,\"1\"]},{\"value\":[1,\"2\"]}]}}"))).isNull();
  }

  private PrometheusMetricsClient source(AtomicInteger calls, int age, boolean down) {
    var client = mock(PrometheusMetricsClient.class);
    when(client.configured()).thenReturn(true);
    when(client.queryInstant(anyString(), any())).thenAnswer(invocation -> {
      calls.incrementAndGet();
      String query = invocation.getArgument(0);
      double value = query.contains("timestamp(") ? now.minusSeconds(age).getEpochSecond()
          : query.contains("node_cpu") ? 63.1 : query.contains("node_memory") ? 42.2
          : query.contains("node_filesystem") ? 90 : down ? 0 : 1;
      if (down && query.contains("timestamp(") && query.contains("== 1")) {
        return CompletableFuture.completedFuture(mapper.readTree("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[]}}"));
      }
      return CompletableFuture.completedFuture(vector(value));
    });
    when(client.queryRange(anyString(), any(), any(), anyLong())).thenReturn(CompletableFuture.completedFuture(mapper.readTree("{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\",\"result\":[]}}")));
    return client;
  }

  private JsonNode vector(double value) {
    return mapper.readTree("{\"status\":\"success\",\"data\":{\"resultType\":\"vector\",\"result\":[{\"metric\":{\"instance\":\"internal-host\"},\"value\":[" + now.getEpochSecond() + ",\"" + value + "\"]}]}}");
  }
}
