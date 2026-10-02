package com.acme.jitsi.domains.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.acme.jitsi.shared.observability.MetricCatalog;
import com.acme.jitsi.shared.observability.ServerMetricsService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class AdminMetricsServiceTest {
  @Test
  void validatesTheClosedPreferenceContractBeforeDatabaseOrSourceAccess() {
    var jdbc = mock(JdbcClient.class);
    var metrics = mock(ServerMetricsService.class);
    org.mockito.Mockito.when(metrics.catalog()).thenReturn(new MetricCatalog().descriptors());
    var service = new AdminMetricsService(jdbc, metrics, JsonMapper.builder().build());
    for (String input : List.of(
        "{\"revision\":0,\"period\":\"1h\",\"widgets\":[],\"tenantId\":\"other\"}",
        "{\"revision\":0,\"period\":\"1h\",\"widgets\":[{\"metricId\":\"host.cpu\",\"view\":\"card\",\"query\":\"up\"}]}",
        "{\"revision\":-1,\"period\":\"1h\",\"widgets\":[]}",
        "{\"revision\":0,\"period\":\"forever\",\"widgets\":[]}",
        "{\"revision\":0,\"period\":\"1h\",\"widgets\":[{\"metricId\":\"other\",\"view\":\"card\"}]}",
        "{\"revision\":0,\"period\":\"1h\",\"widgets\":[{\"metricId\":\"host.cpu\",\"view\":\"raw\"}]}",
        "{\"revision\":0,\"period\":\"1h\",\"widgets\":[{\"metricId\":\"host.cpu\",\"view\":\"card\"},{\"metricId\":\"host.cpu\",\"view\":\"line\"}]}")) {
      assertThatThrownBy(() -> service.readDashboard(input.getBytes(StandardCharsets.UTF_8)))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(service.readDashboard("{\"revision\":0,\"period\":\"1h\",\"widgets\":[]}".getBytes(StandardCharsets.UTF_8)).widgets()).isEmpty();
    verifyNoInteractions(jdbc);
  }
}
