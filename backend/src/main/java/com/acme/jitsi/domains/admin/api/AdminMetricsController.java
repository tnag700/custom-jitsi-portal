package com.acme.jitsi.domains.admin.api;

import com.acme.jitsi.domains.admin.service.AdminMetricsService;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.shared.ErrorCode;
import com.acme.jitsi.shared.observability.ServerMetricsService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/admin/metrics", version = "v1")
public class AdminMetricsController {
  private final AdminMetricsService metrics;
  private final TenantAccessGuard tenants;
  private final ProblemResponseFacade problems;
  public AdminMetricsController(AdminMetricsService metrics, TenantAccessGuard tenants, ProblemResponseFacade problems) {
    this.metrics = metrics; this.tenants = tenants; this.problems = problems;
  }

  private void owner(OAuth2User principal) { tenants.resolveTenantId(principal); subject(principal); }
  private String subject(OAuth2User principal) {
    Object value = principal.getAttribute("sub");
    if (!(value instanceof String id) || id.isBlank() || id.length() > 255) throw new AccessDeniedException("Subject claim is required");
    return id;
  }

  @GetMapping({"", "/", "/catalog"})
  public List<ServerMetricsService.MetricDescriptor> catalog(@AuthenticationPrincipal OAuth2User principal) {
    owner(principal); return metrics.catalog();
  }

  @GetMapping("/query")
  public ServerMetricsService.MetricsSnapshot query(@AuthenticationPrincipal OAuth2User principal,
      @RequestParam List<String> ids, @RequestParam(defaultValue = "1h") String period) {
    owner(principal); return metrics.query(ids, period);
  }

  @GetMapping("/dashboard")
  public AdminMetricsService.Dashboard dashboard(@AuthenticationPrincipal OAuth2User principal) {
    return metrics.loadDashboard(tenants.resolveTenantId(principal), subject(principal));
  }

  @PutMapping(value = "/dashboard", consumes = "application/json")
  @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
      content = @Content(schema = @Schema(implementation = AdminMetricsService.Dashboard.class)))
  @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
      content = @Content(schema = @Schema(implementation = AdminMetricsService.Dashboard.class)))
  public ResponseEntity<?> save(@AuthenticationPrincipal OAuth2User principal, HttpServletRequest request) throws IOException {
    String tenant = tenants.resolveTenantId(principal);
    String subject = subject(principal);
    byte[] body = request.getInputStream().readNBytes(16_385);
    if (body.length > 16_384) return problem(request, HttpStatus.PAYLOAD_TOO_LARGE, "Настройки слишком велики", ErrorCode.INVALID_REQUEST.code());
    return ResponseEntity.ok(metrics.saveDashboard(tenant, subject, metrics.readDashboard(body)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> invalid(HttpServletRequest request) {
    return problem(request, HttpStatus.BAD_REQUEST, "Некорректные настройки или выбор метрик", ErrorCode.INVALID_REQUEST.code());
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ResponseEntity<ProblemDetail> conflict(HttpServletRequest request) {
    return problem(request, HttpStatus.CONFLICT, "Настройки изменились. Перезагрузите их перед сохранением.", ErrorCode.INVALID_REQUEST.code());
  }

  private ResponseEntity<ProblemDetail> problem(HttpServletRequest request, HttpStatus status, String detail, String code) {
    return ResponseEntity.status(status).body(problems.buildProblemDetail(request, status, "Метрики сервера", detail, code));
  }
}
