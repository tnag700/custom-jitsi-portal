package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.dto.AdminDashboardDrillDownResponse;
import com.acme.jitsi.domains.admin.dto.AdminDashboardSummaryResponse;
import com.acme.jitsi.domains.admin.service.AdminDashboardService;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/admin/dashboard", version = "v1")
public class AdminDashboardController {

  private final AdminDashboardService adminDashboardService;
  private final ProblemResponseFacade problemResponseFacade;
  private final TenantAccessGuard tenantAccessGuard;

  public AdminDashboardController(
      AdminDashboardService adminDashboardService,
      ProblemResponseFacade problemResponseFacade,
      TenantAccessGuard tenantAccessGuard) {
    this.adminDashboardService = adminDashboardService;
    this.problemResponseFacade = problemResponseFacade;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @GetMapping
  public AdminDashboardSummaryResponse getSummary(
      @AuthenticationPrincipal OAuth2User principal,
      @RequestParam(defaultValue = "15m") String period,
      @RequestParam(required = false) String environment,
      @RequestParam(required = false) String roomId,
      @RequestParam(required = false) String meetingId,
      HttpServletRequest request) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    String traceId = problemResponseFacade.resolveTraceId(request);
    return adminDashboardService.getSummary(tenantId, period, environment, roomId, meetingId, traceId);
  }

  @GetMapping("/drill-down")
  public AdminDashboardDrillDownResponse getDrillDown(
      @AuthenticationPrincipal OAuth2User principal,
      @RequestParam(defaultValue = "15m") String period,
      @RequestParam(required = false) String environment,
      @RequestParam(required = false) String roomId,
      @RequestParam(required = false) String meetingId,
      @RequestParam(required = false) String errorCode,
      @RequestParam(required = false) String category) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminDashboardService.getDrillDown(tenantId, period, environment, roomId, meetingId, errorCode, category);
  }
}