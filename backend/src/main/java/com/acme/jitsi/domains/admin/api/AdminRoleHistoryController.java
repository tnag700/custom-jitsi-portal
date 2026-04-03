package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.dto.AdminRoleHistoryResponse;
import com.acme.jitsi.domains.admin.service.AdminRoleHistoryService;
import com.acme.jitsi.security.TenantAccessGuard;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/admin/role-history", version = "v1")
public class AdminRoleHistoryController {

  private final AdminRoleHistoryService adminRoleHistoryService;
  private final TenantAccessGuard tenantAccessGuard;

  public AdminRoleHistoryController(
      AdminRoleHistoryService adminRoleHistoryService,
      TenantAccessGuard tenantAccessGuard) {
    this.adminRoleHistoryService = adminRoleHistoryService;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @GetMapping
  public AdminRoleHistoryResponse getRoleHistory(
      @AuthenticationPrincipal OAuth2User principal,
      @RequestParam(required = false) String environment,
      @RequestParam(name = "q", required = false) String query,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String actionType,
      @RequestParam(required = false) String role,
      @RequestParam(required = false) String actorId,
      @RequestParam(required = false) String subjectId,
      @RequestParam(required = false) String roomId,
      @RequestParam(required = false) String meetingId,
      @RequestParam(required = false) String traceId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminRoleHistoryService.getRoleHistory(
        tenantId,
        authorities(principal),
        new AdminRoleHistoryService.AdminRoleHistoryQuery(
            environment,
            query,
            from,
            to,
            actionType,
            role,
            actorId,
            subjectId,
            roomId,
            meetingId,
            traceId,
            page,
            pageSize));
  }

  private List<String> authorities(OAuth2User principal) {
    if (principal == null || principal.getAuthorities() == null) {
      return List.of();
    }
    return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }
}