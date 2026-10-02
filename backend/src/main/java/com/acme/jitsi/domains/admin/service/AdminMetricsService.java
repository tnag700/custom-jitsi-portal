package com.acme.jitsi.domains.admin.service;

import com.acme.jitsi.shared.observability.ServerMetricsService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AdminMetricsService {
  public record Widget(String metricId, String view) {}
  public record Dashboard(long revision, String period, List<Widget> widgets) {}
  private static final Logger log = LoggerFactory.getLogger(AdminMetricsService.class);
  private final JdbcClient jdbc;
  private final ServerMetricsService metrics;
  private final JsonMapper mapper;

  public AdminMetricsService(JdbcClient jdbc, ServerMetricsService metrics, JsonMapper mapper) {
    this.jdbc = jdbc; this.metrics = metrics; this.mapper = mapper;
  }

  public List<ServerMetricsService.MetricDescriptor> catalog() { return metrics.catalog(); }
  public ServerMetricsService.MetricsSnapshot query(List<String> ids, String period) {
    return metrics.query(ids, ServerMetricsService.MetricPeriod.parse(period));
  }

  public Dashboard loadDashboard(String tenantId, String subjectId) {
    return jdbc.sql("SELECT revision, CAST(layout AS VARCHAR) AS layout FROM metric_dashboard_preferences WHERE tenant_id=:tenant AND subject_id=:subject")
        .param("tenant", tenantId).param("subject", subjectId).query((rs, row) -> {
          var layout = mapper.readTree(rs.getString("layout"));
          var widgets = new ArrayList<Widget>();
          for (var widget : layout.path("widgets")) {
            String id = widget.path("metricId").asString();
            String view = widget.path("view").asString();
            if (catalog().stream().anyMatch(d -> d.id().equals(id) && d.views().contains(view))) {
              widgets.add(new Widget(id, view));
            }
          }
          if (widgets.size() < layout.path("widgets").size()) log.warn("Ignored obsolete dashboard metrics: {}", layout.path("widgets").size() - widgets.size());
          return new Dashboard(rs.getLong("revision"), ServerMetricsService.MetricPeriod.parse(layout.path("period").asString()).value(), List.copyOf(widgets));
        }).optional().orElseGet(() -> new Dashboard(0, "1h",
            List.of("host.cpu", "host.memory", "host.disk", "jvm.heap", "jdbc.pool", "jwt.latency-p95")
                .stream().map(id -> new Widget(id, "card")).toList()));
  }

  @Transactional
  public Dashboard saveDashboard(String tenantId, String subjectId, Dashboard input) {
    validate(input);
    String sql = input.revision() == 0
        ? "INSERT INTO metric_dashboard_preferences (tenant_id, subject_id, revision, layout) VALUES (:tenant, :subject, 1, CAST(:layout AS JSONB)) ON CONFLICT (tenant_id, subject_id) DO NOTHING"
        : "UPDATE metric_dashboard_preferences SET revision=revision+1, layout=CAST(:layout AS JSONB), updated_at=CURRENT_TIMESTAMP WHERE tenant_id=:tenant AND subject_id=:subject AND revision=:revision";
    var statement = jdbc.sql(sql).param("tenant", tenantId).param("subject", subjectId)
        .param("layout", mapper.writeValueAsString(Map.of("period", input.period(), "widgets", input.widgets())));
    if (input.revision() != 0) statement.param("revision", input.revision());
    if (statement.update() != 1) throw new OptimisticLockingFailureException("Dashboard revision changed");
    return new Dashboard(input.revision() + 1, input.period(), List.copyOf(input.widgets()));
  }

  public Dashboard readDashboard(byte[] body) {
    try {
      var node = mapper.readTree(body);
      fields(node, Set.of("revision", "period", "widgets"));
      if (!node.get("revision").isIntegralNumber() || !node.get("revision").canConvertToLong() || !node.get("period").isString()
          || !node.get("widgets").isArray() || node.get("widgets").size() > 12) throw new IllegalArgumentException("Invalid dashboard");
      var widgets = new ArrayList<Widget>();
      for (var widget : node.get("widgets")) {
        fields(widget, Set.of("metricId", "view"));
        if (!widget.get("metricId").isString() || !widget.get("view").isString()) throw new IllegalArgumentException("Invalid widget");
        widgets.add(new Widget(widget.get("metricId").asString(), widget.get("view").asString()));
      }
      var dashboard = new Dashboard(node.get("revision").longValue(), node.get("period").asString(), List.copyOf(widgets));
      validate(dashboard);
      return dashboard;
    } catch (tools.jackson.core.JacksonException malformed) {
      throw new IllegalArgumentException("Invalid dashboard JSON", malformed);
    }
  }

  private static void fields(JsonNode node, Set<String> expected) {
    if (node == null || !node.isObject() || !node.propertyNames().equals(expected)) throw new IllegalArgumentException("Unknown or missing fields");
  }

  private void validate(Dashboard input) {
    if (input == null || input.revision() < 0 || input.revision() == Long.MAX_VALUE || input.widgets() == null || input.widgets().size() > 12) throw new IllegalArgumentException("Invalid dashboard");
    ServerMetricsService.MetricPeriod.parse(input.period());
    var ids = new HashSet<String>();
    for (var widget : input.widgets()) {
      if (widget == null || !ids.add(widget.metricId()) || catalog().stream().noneMatch(d -> d.id().equals(widget.metricId()) && d.views().contains(widget.view()))) throw new IllegalArgumentException("Invalid widget");
    }
  }
}
