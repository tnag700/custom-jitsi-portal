package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.dto.AdminIncidentCoordinationUpdateRequest;
import com.acme.jitsi.domains.admin.dto.AdminIncidentDetailResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentListResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentSearchResponse;
import com.acme.jitsi.domains.admin.dto.AdminIncidentTicketResponse;
import com.acme.jitsi.domains.admin.service.AdminIncidentsService;
import com.acme.jitsi.security.TenantAccessGuard;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/admin/incidents", version = "v1")
public class AdminIncidentsController {

  private final AdminIncidentsService adminIncidentsService;
  private final TenantAccessGuard tenantAccessGuard;

  public AdminIncidentsController(
      AdminIncidentsService adminIncidentsService,
      TenantAccessGuard tenantAccessGuard) {
    this.adminIncidentsService = adminIncidentsService;
    this.tenantAccessGuard = tenantAccessGuard;
  }

  @GetMapping
  public AdminIncidentListResponse listIncidents(
      @AuthenticationPrincipal OAuth2User principal,
      @RequestParam(defaultValue = "15m") String period,
      @RequestParam(required = false) String environment,
      @RequestParam(defaultValue = "active") String view,
      @RequestParam(required = false) String facet,
      @RequestParam(required = false) String roomId,
      @RequestParam(required = false) String meetingId,
      @RequestParam(required = false) String subjectId,
      @RequestParam(required = false) String errorCode,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) String severity,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset,
      @RequestParam(defaultValue = "queue") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminIncidentsService.listIncidents(
        tenantId,
        authorities(principal),
        new AdminIncidentsService.AdminIncidentListQuery(
            period,
            environment,
          view,
          facet,
            roomId,
            meetingId,
            subjectId,
            errorCode,
            category,
            severity,
            limit,
            offset,
            sort,
            direction));
  }

  @GetMapping("/{incidentId}")
  public AdminIncidentDetailResponse getIncidentDetail(
      @AuthenticationPrincipal OAuth2User principal,
      @PathVariable("incidentId") String incidentId,
      @RequestParam(required = false) String environment) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminIncidentsService.getIncidentDetail(tenantId, authorities(principal), incidentId, environment);
  }

  @GetMapping("/search")
  public AdminIncidentSearchResponse searchIncidents(
      @AuthenticationPrincipal OAuth2User principal,
      @RequestParam(required = false) String environment,
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) String requestId,
      @RequestParam(required = false) String errorCode,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String meetingId) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminIncidentsService.searchIncidents(
        tenantId,
        authorities(principal),
        new AdminIncidentsService.AdminIncidentSearchQuery(
            environment,
            traceId,
            requestId,
            errorCode,
            from,
            to,
            meetingId));
  }

  @PostMapping("/{incidentId}/ticket")
  public AdminIncidentTicketResponse createTicket(
      @AuthenticationPrincipal OAuth2User principal,
      @PathVariable("incidentId") String incidentId,
      @RequestParam(required = false) String environment) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminIncidentsService.createTicket(tenantId, authorities(principal), incidentId, environment, actorId(principal));
  }

  @PostMapping("/{incidentId}/coordination")
  public AdminIncidentDetailResponse.CoordinationState updateCoordination(
      @AuthenticationPrincipal OAuth2User principal,
      @PathVariable("incidentId") String incidentId,
      @RequestParam(required = false) String environment,
      @RequestBody AdminIncidentCoordinationUpdateRequest request) {
    String tenantId = tenantAccessGuard.resolveTenantId(principal);
    return adminIncidentsService.updateCoordination(
        tenantId,
        authorities(principal),
        incidentId,
        environment,
        request,
        actorId(principal));
  }

  private List<String> authorities(OAuth2User principal) {
    if (principal == null || principal.getAuthorities() == null) {
      return List.of();
    }
    return principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
  }

  private String actorId(OAuth2User principal) {
    if (principal == null) {
      return "admin";
    }
    Object subject = principal.getAttribute("sub");
    if (subject instanceof String subjectId && !subjectId.isBlank()) {
      return subjectId;
    }
    return principal.getName();
  }
}