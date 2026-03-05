package com.acme.jitsi.security;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);
  private static final String UTF_8_CHARSET = StandardCharsets.UTF_8.name();
  private final JsonMapper jsonMapper;
  private final ProblemResponseFacade problemResponseFacade;
  private final ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  public SecurityConfig(
      JsonMapper jsonMapper,
      ProblemResponseFacade problemResponseFacade,
      ProblemDetailsMappingPolicy problemDetailsMappingPolicy) {
    this.jsonMapper = jsonMapper;
    this.problemResponseFacade = problemResponseFacade;
    this.problemDetailsMappingPolicy = problemDetailsMappingPolicy;
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(frontendOrigin));
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "X-XSRF-TOKEN", "X-Trace-Id"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }

  @Bean
  OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService(
      OidcClaimsValidator claimsValidator,
      OidcRoleAuthoritiesMapper roleAuthoritiesMapper,
      @Value("${app.security.sso.expected-issuer:http://localhost:8081/realms/jitsi-dev}") String expectedIssuer,
      @Value("${spring.security.oauth2.client.registration.keycloak.client-id:jitsi-backend}") String clientId) {
    OidcUserService delegate = new OidcUserService();

    return userRequest -> {
      OidcUser user = delegate.loadUser(userRequest);
      OidcIdToken idToken = userRequest.getIdToken();
      claimsValidator.validate(idToken, expectedIssuer, clientId);
      Map<String, Object> accessTokenClaims = extractAccessTokenClaims(userRequest.getAccessToken().getTokenValue());

      Map<String, Object> mergedUserInfoClaims = new java.util.HashMap<>();
      if (user.getUserInfo() != null) {
        mergedUserInfoClaims.putAll(user.getUserInfo().getClaims());
      }
      mergedUserInfoClaims.putAll(accessTokenClaims);
      org.springframework.security.oauth2.core.oidc.OidcUserInfo customUserInfo = new org.springframework.security.oauth2.core.oidc.OidcUserInfo(
          mergedUserInfoClaims);

      return new DefaultOidcUser(
          roleAuthoritiesMapper.mapAuthorities(user, accessTokenClaims),
          user.getIdToken(),
          customUserInfo);
    };
  }

  private Map<String, Object> extractAccessTokenClaims(String tokenValue) {
    try {
      SignedJWT signedJwt = SignedJWT.parse(tokenValue);
      JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
      if (claimsSet == null) {
        return Map.of();
      }
      Map<String, Object> claims = extractClaimsMap(claimsSet);
      return claims == null ? Map.of() : claims;
    } catch (ParseException ex) {
      if (log.isDebugEnabled()) {
        log.debug("oidc_access_token_parse_failed message={}", ex.getMessage());
      }
      return Map.of();
    }
  }

  @Bean
  AuthenticationEntryPoint jsonAuthenticationEntryPoint() {
    return (request, response, authException) -> {
      ProblemDetailsMappingPolicy.ProblemDefinition definition = problemDetailsMappingPolicy.mapSecurityAuthRequired();
      String traceId = resolveTraceId(request);
      if (log.isWarnEnabled()) {
        log.warn(
            "authentication_required code=AUTH_REQUIRED path={} traceId={} exceptionType={}",
            request.getRequestURI(),
            traceId,
            authException == null ? "unknown" : authException.getClass().getSimpleName());
      }
      writeProblem(
          request,
          response,
          definition.status(),
          definition.title(),
          definition.detail(),
          definition.errorCode());
    };
  }

  @Bean
  AccessDeniedHandler jsonAccessDeniedHandler() {
    return (request, response, accessDeniedException) -> {
      ProblemDetailsMappingPolicy.ProblemDefinition definition = problemDetailsMappingPolicy.mapSecurityAccessDenied();
      String traceId = resolveTraceId(request);
      logMaskedErrorDispatchIfPresent(request, traceId, accessDeniedException);
      if (log.isWarnEnabled()) {
        log.warn(
            "access_denied code=ACCESS_DENIED path={} traceId={} exceptionType={}",
            request.getRequestURI(),
            traceId,
            accessDeniedException == null ? "unknown" : accessDeniedException.getClass().getSimpleName());
      }
      writeProblem(
          request,
          response,
          definition.status(),
          definition.title(),
          definition.detail(),
          definition.errorCode());
    };
  }

  private void writeProblem(
      HttpServletRequest request,
      jakarta.servlet.http.HttpServletResponse response,
      HttpStatus status,
      String title,
      String detail,
      String errorCode) throws java.io.IOException {
    String traceId = resolveTraceId(request);
    if (log.isInfoEnabled()) {
      log.info(
        "problem_response status={} code={} path={} traceId={}",
        status.value(),
        errorCode,
        request.getRequestURI(),
        traceId);
    }
    Map<String, Object> payload = problemResponseFacade.buildProblemPayload(request, status, title, detail, errorCode);

    response.setStatus(status.value());
    response.setCharacterEncoding(UTF_8_CHARSET);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    String payloadJson = jsonMapper.writeValueAsString(payload);
    response.getWriter().write(payloadJson);
  }

  private String resolveTraceId(HttpServletRequest request) {
    return problemResponseFacade.resolveTraceId(request);
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationEntryPoint jsonAuthenticationEntryPoint,
      AccessDeniedHandler jsonAccessDeniedHandler,
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin,
      OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService) throws Exception {
    http.cors(Customizer.withDefaults());
    http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
    http.requestCache(cache -> cache.disable());
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers(
          "/error",
            "/actuator/health",
            "/api/v1/health",
            "/api/v1/auth/login",
            "/api/v1/auth/error",
            "/api/v1/auth/csrf",
            "/api/v1/auth/refresh",
            "/api/v1/invites/exchange",
            "/api/v1/invites/*/validate",
            "/login/oauth2/**",
            "/oauth2/**")
        .permitAll()
        .requestMatchers("/api/v1/auth/me")
        .authenticated()
        .requestMatchers("/api/v1/auth/refresh/revoke")
        .authenticated()
        .requestMatchers("/api/v1/meetings/upcoming")
        .authenticated()
        .requestMatchers("/api/v1/meetings/*/access-token")
        .authenticated()
        .requestMatchers("/api/v1/meetings/**")
        .hasRole("admin")
        // AC1: Room management requires admin role
        .requestMatchers("/api/v1/rooms/**")
        .hasRole("admin")
        .requestMatchers("/api/v1/config-sets/**")
        .hasRole("admin")
        .requestMatchers("/api/v1/profile/**")
        .authenticated()
        .requestMatchers("/api/v1/users/**")
        .authenticated()
        .anyRequest().denyAll());
    http.exceptionHandling(ex -> ex
        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
        .accessDeniedHandler(jsonAccessDeniedHandler));

    HttpSecurity configured = http;

    if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
      configured.oauth2Login(oauth -> oauth
          .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
          .successHandler(
              (request, response, authentication) -> response.sendRedirect(frontendOrigin + "/auth/continue"))
          .failureHandler((request, response, exception) -> {
            String traceId = resolveTraceId(request);
            Throwable cause = exception == null ? null : exception.getCause();
            String exceptionType = exception == null ? "unknown" : exception.getClass().getSimpleName();
            String exceptionMessage = exception == null ? "" : exception.getMessage();
            String causeType = cause == null ? "" : cause.getClass().getSimpleName();
            String causeMessage = cause == null ? "" : cause.getMessage();
            if (log.isWarnEnabled()) {
              log.warn(
                "oidc_login_failure code=ACCESS_DENIED path={} traceId={} exceptionType={} exceptionMessage={} causeType={} causeMessage={}",
                request.getRequestURI(),
                traceId,
                exceptionType,
                exceptionMessage,
                causeType,
                causeMessage);
            }
            response.sendRedirect("/api/v1/auth/error?code=ACCESS_DENIED");
          }));
    }

    return buildFilterChain(configured);
  }

  private Map<String, Object> extractClaimsMap(JWTClaimsSet claimsSet) {
    return claimsSet.getClaims();
  }

  private SecurityFilterChain buildFilterChain(HttpSecurity configured) throws Exception {
    return configured.build();
  }

          private void logMaskedErrorDispatchIfPresent(
              HttpServletRequest request,
              String traceId,
              Exception accessDeniedException) {
            if (!"/error".equals(request.getRequestURI())) {
              return;
            }
            Object statusCode = request.getAttribute("jakarta.servlet.error.status_code");
            Object originUri = request.getAttribute("jakarta.servlet.error.request_uri");
            Object originException = request.getAttribute("jakarta.servlet.error.exception");
            String originExceptionType = resolveExceptionType(originException);
            String originExceptionMessage = originException == null ? "" : String.valueOf(originException);
            String securityExceptionType = resolveExceptionType(accessDeniedException);
            if (log.isErrorEnabled()) {
              log.error(
                  "masked_error_dispatch path=/error traceId={} originalStatus={} originalPath={} originalExceptionType={} originalException={} securityExceptionType={}",
                  traceId,
                  statusCode,
                  originUri,
                  originExceptionType,
                  originExceptionMessage,
                  securityExceptionType);
            }
          }

  private String resolveExceptionType(Object exceptionObject) {
    if (exceptionObject == null) {
      return "unknown";
    }
    if (exceptionObject instanceof IllegalArgumentException) {
      return "IllegalArgumentException";
    }
    if (exceptionObject instanceof IllegalStateException) {
      return "IllegalStateException";
    }
    if (exceptionObject instanceof RuntimeException) {
      return "RuntimeException";
    }
    if (exceptionObject instanceof Exception) {
      return "Exception";
    }
    return "Throwable";
  }
}
