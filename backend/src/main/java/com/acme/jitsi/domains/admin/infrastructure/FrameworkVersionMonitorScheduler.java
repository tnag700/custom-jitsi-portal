package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.FrameworkVersionMonitorService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "app.version-monitor.schedule",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class FrameworkVersionMonitorScheduler {

  private final FrameworkVersionMonitorService monitorService;

  FrameworkVersionMonitorScheduler(FrameworkVersionMonitorService monitorService) {
    this.monitorService = monitorService;
  }

  @Scheduled(
      initialDelayString = "${app.version-monitor.schedule.initial-delay:PT1M}",
      fixedDelayString = "${app.version-monitor.schedule.fixed-delay:PT6H}")
  void refreshFrameworkVersions() {
    monitorService.refresh();
  }
}
