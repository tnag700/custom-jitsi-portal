package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.dto.AdminFrameworkVersionsResponse;
import com.acme.jitsi.domains.admin.service.FrameworkVersionMonitorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/admin/framework-versions", version = "v1")
public class AdminFrameworkVersionsController {

  private final FrameworkVersionMonitorService monitorService;

  public AdminFrameworkVersionsController(FrameworkVersionMonitorService monitorService) {
    this.monitorService = monitorService;
  }

  @GetMapping
  public AdminFrameworkVersionsResponse getCurrent() {
    return monitorService.getCurrent();
  }

  @PostMapping("/refresh")
  public AdminFrameworkVersionsResponse refresh() {
    return monitorService.refresh();
  }
}
