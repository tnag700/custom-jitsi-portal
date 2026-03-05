package com.acme.jitsi.domains.auth.api;

import com.acme.jitsi.domains.auth.service.AuthRefreshService;
import com.acme.jitsi.domains.auth.service.SafeUserProfileMapper;
import com.acme.jitsi.security.ProblemDetailsFactory;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import java.net.URI;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/auth", version = "v1")
class AuthController {

  private final AuthRefreshService authRefreshService;
  private final SafeUserProfileMapper safeUserProfileMapper;
  private final ProblemDetailsFactory problemDetailsFactory;
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final String frontendOrigin;

  AuthController(
      AuthRefreshService authRefreshService,
      SafeUserProfileMapper safeUserProfileMapper,
      ProblemDetailsFactory problemDetailsFactory,
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin) {
    this.authRefreshService = authRefreshService;
    this.safeUserProfileMapper = safeUserProfileMapper;
    this.problemDetailsFactory = problemDetailsFactory;
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
    this.frontendOrigin = frontendOrigin;
  }

  @GetMapping("/login")
  ResponseEntity<Void> login() {
    return ResponseEntity.status(302)
        .location(URI.create("/oauth2/authorization/keycloak"))
        .build();
  }

  @GetMapping("/error")
  ResponseEntity<Map<String, Object>> error(
      @RequestParam(name = "code", defaultValue = "AUTH_REQUIRED") String code,
      HttpServletRequest request) {
    ProblemDetailsMappingPolicy.ProblemDefinition definition =
      problemDetailsMappingPolicy.mapAuthErrorCode(code);

    return ResponseEntity.status(definition.status())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
      .body(problemDetailsFactory.build(
            request,
        definition.status(),
        definition.title(),
        definition.detail(),
        definition.errorCode()));
  }

  @GetMapping("/me")
  SafeUserProfileResponse me(@AuthenticationPrincipal OAuth2User principal) {
    return safeUserProfileMapper.fromPrincipal(principal);
  }

  @GetMapping(value = "/me", params = "continue")
  ResponseEntity<Void> meContinue() {
    return ResponseEntity.status(302)
        .location(URI.create(frontendOrigin + "/auth/continue"))
        .build();
  }

  @GetMapping("/csrf")
  Map<String, String> csrf(CsrfToken csrfToken) {
    return Map.of(
        "token", csrfToken.getToken(),
        "headerName", csrfToken.getHeaderName());
  }

  @PostMapping("/refresh")
  AuthRefreshResponse refresh(@Valid @RequestBody AuthRefreshRequest request) {
    AuthRefreshService.RefreshResult result = authRefreshService.refresh(request.refreshToken());
    return new AuthRefreshResponse(
        result.accessToken(),
        result.refreshToken(),
        result.expiresAt(),
        result.role(),
        result.tokenType());
  }

  @PostMapping("/refresh/revoke")
  ResponseEntity<Void> revokeRefresh(@Valid @RequestBody AuthRefreshRevokeRequest request) {
    authRefreshService.revoke(request.tokenId());
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }
}
