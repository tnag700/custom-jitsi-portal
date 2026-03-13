package com.acme.jitsi.domains.auth.api;

import com.acme.jitsi.domains.auth.service.AuthRefreshService;
import com.acme.jitsi.domains.auth.service.AuthLogoutService;
import com.acme.jitsi.security.ProblemDetailsFactory;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.shared.ErrorCode;
import java.net.URI;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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

  private static final String SESSION_COOKIE = "JSESSIONID";
  private static final String CSRF_COOKIE = "XSRF-TOKEN";

  private final AuthRefreshService authRefreshService;
  private final AuthLogoutService authLogoutService;
  private final SafeUserProfileResponseMapper safeUserProfileResponseMapper;
  private final ProblemDetailsFactory problemDetailsFactory;
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;
  private final String frontendOrigin;

  AuthController(
      AuthRefreshService authRefreshService,
      AuthLogoutService authLogoutService,
      SafeUserProfileResponseMapper safeUserProfileResponseMapper,
      ProblemDetailsFactory problemDetailsFactory,
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy,
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin) {
    this.authRefreshService = authRefreshService;
    this.authLogoutService = authLogoutService;
    this.safeUserProfileResponseMapper = safeUserProfileResponseMapper;
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
      @RequestParam(name = "code", required = false) String code,
      HttpServletRequest request) {
    String resolvedCode = code != null ? code : ErrorCode.AUTH_REQUIRED.code();
    ProblemDetailsMappingPolicy.ProblemDefinition definition =
      problemDetailsMappingPolicy.mapAuthErrorCode(resolvedCode);

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
    return safeUserProfileResponseMapper.fromPrincipal(principal);
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

  @PostMapping("/logout")
  void logout(
      Authentication authentication,
      HttpServletRequest request,
      HttpServletResponse response) throws java.io.IOException {
    URI redirectUri = authLogoutService.resolveLogoutRedirect(authentication);

    clearAuthCookies(response);
    new SecurityContextLogoutHandler().logout(request, response, authentication);

    response.setStatus(HttpStatus.FOUND.value());
    response.setHeader("Location", redirectUri.toString());
  }

  private void clearAuthCookies(HttpServletResponse response) {
    response.addHeader("Set-Cookie", expiredCookie(SESSION_COOKIE, true));
    response.addHeader("Set-Cookie", expiredCookie(CSRF_COOKIE, false));
  }

  private String expiredCookie(String name, boolean httpOnly) {
    return ResponseCookie.from(name, "")
        .path("/")
        .httpOnly(httpOnly)
        .maxAge(0)
        .sameSite("Lax")
        .build()
        .toString();
  }
}
