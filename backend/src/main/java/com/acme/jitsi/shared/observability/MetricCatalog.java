package com.acme.jitsi.shared.observability;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MetricCatalog {
  record Definition(ServerMetricsService.MetricDescriptor descriptor, String query,
                    String samples, String job, String traffic) {
    String freshnessQuery() {
      String timestamp = "min(timestamp(" + samples + "))";
      return descriptor.id().equals("backend.available") ? timestamp
          : timestamp + " and on() (min(up{job=\"" + job + "\"}) == 1)"
              + " and on() (count(up{job=\"" + job + "\"}) == 1)";
    }

    String rangeQuery() {
      return "(" + query + ") and on() ((time() - (" + freshnessQuery() + ")) <= 90)";
    }
  }

  private final List<Definition> definitions = List.of(
      metric("host.cpu", "Процессор", "Загрузка CPU сервера за 5 минут", "percent", "jitsi-node",
          "100 * (1 - avg(rate(node_cpu_seconds_total{job=\"jitsi-node\",mode=\"idle\"}[5m])))", "node_cpu_seconds_total", null),
      metric("host.memory", "Оперативная память", "Занятая RAM с учётом доступного файлового кэша", "percent", "jitsi-node",
          "100 * (1 - sum(node_memory_MemAvailable_bytes{job=\"jitsi-node\"}) / sum(node_memory_MemTotal_bytes{job=\"jitsi-node\"}))", "node_memory_MemAvailable_bytes|node_memory_MemTotal_bytes", null),
      metric("host.disk", "Системный диск", "Занятое место: предупреждение от 90%, критическое от 95%", "percent", "jitsi-node",
          "100 * (1 - sum(node_filesystem_avail_bytes{job=\"jitsi-node\",mountpoint=\"/\"}) / sum(node_filesystem_size_bytes{job=\"jitsi-node\",mountpoint=\"/\"}))", "node_filesystem_avail_bytes|node_filesystem_size_bytes", null),
      metric("backend.available", "Доступность backend", "Успешность сбора метрик backend; не подтверждает медиасвязь", "boolean", "jitsi-backend", "up{job=\"jitsi-backend\"}", "up", null),
      metric("backend.join-ready", "Готовность подключения", "Готовность backend выдать токен подключения", "boolean", "jitsi-backend", "jitsi_service_join_readiness_ready{job=\"jitsi-backend\"}", "jitsi_service_join_readiness_ready", null),
      metric("jvm.heap", "Память JVM", "Использованная heap-память backend", "bytes", "jitsi-backend", "sum(jvm_memory_used_bytes{job=\"jitsi-backend\",area=\"heap\"})", "jvm_memory_used_bytes", null),
      metric("jdbc.pool", "Пул соединений БД", "Активные соединения относительно максимума", "percent", "jitsi-backend", "100 * sum(hikaricp_connections_active{job=\"jitsi-backend\"}) / sum(hikaricp_connections_max{job=\"jitsi-backend\"})", "hikaricp_connections_active|hikaricp_connections_max", null),
      metric("jwt.issued", "Выдача токенов", "Успешные выдачи JWT в минуту; не число звонков", "per_minute", "jitsi-backend", "60 * sum(rate(jitsi_join_success_total{job=\"jitsi-backend\"}[5m]))", "jitsi_join_success_total", null),
      metric("jwt.error-ratio", "Ошибки выдачи токенов", "Доля неудачных запросов JWT за 5 минут", "percent", "jitsi-backend", "100 * sum(rate(jitsi_join_failure_total{job=\"jitsi-backend\"}[5m])) / sum(rate(jitsi_join_attempts_total{job=\"jitsi-backend\"}[5m]))", "jitsi_join_failure_total|jitsi_join_attempts_total", "sum(rate(jitsi_join_attempts_total{job=\"jitsi-backend\"}[5m]))"),
      metric("jwt.latency-p95", "Время выдачи токена p95", "95-й процентиль успешной выдачи JWT за 5 минут", "milliseconds", "jitsi-backend", "1000 * histogram_quantile(0.95, sum by (le)(rate(jitsi_join_latency_seconds_bucket{job=\"jitsi-backend\",result=\"success\"}[5m])))", "jitsi_join_latency_seconds_bucket", "sum(rate(jitsi_join_success_total{job=\"jitsi-backend\"}[5m]))")
  );

  private static Definition metric(String id, String title, String description, String unit,
                                   String job, String query, String sampleNames, String traffic) {
    return new Definition(new ServerMetricsService.MetricDescriptor(id, title, description, unit,
        "SYSTEM", List.of("card", "line")), query,
        "{__name__=~\"" + sampleNames + "|up\",job=\"" + job + "\"}", job, traffic);
  }

  public List<ServerMetricsService.MetricDescriptor> descriptors() {
    return definitions.stream().map(Definition::descriptor).toList();
  }

  Definition require(String id) {
    return definitions.stream().filter(d -> d.descriptor().id().equals(id)).findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown metric"));
  }
}
