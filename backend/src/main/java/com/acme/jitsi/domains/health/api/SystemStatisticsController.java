package com.acme.jitsi.domains.health.api;

import com.acme.jitsi.shared.observability.ServerMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/system/statistics", version = "v1")
public class SystemStatisticsController {
  private final ServerMetricsService metrics;

  public SystemStatisticsController(ServerMetricsService metrics) { this.metrics = metrics; }

  @GetMapping
  public ResponseEntity<ServerMetricsService.Summary> summary() {
    return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(metrics.summary());
  }
}
