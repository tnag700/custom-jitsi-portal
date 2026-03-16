package com.acme.jitsi.security;

import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String BASELINE_CSP =
      "default-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; object-src 'none'";

  private static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=()";

  private static final String[] PUBLIC_ENDPOINTS = {
    "/error",
    "/actuator/health",
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/v3/api-docs.yaml",
    "/api/v1/health",
    "/api/v1/health/join-readiness",
    "/api/v1/auth/login",
    "/api/v1/auth/error",
    "/api/v1/auth/csrf",
    "/api/v1/auth/refresh",
    "/api/v1/invites/exchange",
    "/api/v1/invites/*/validate",
    "/login/oauth2/**",
    "/oauth2/**"
  };

  private static final String[] AUTHENTICATED_ENDPOINTS = {
    "/api/v1/auth/me",
    "/api/v1/auth/logout",
    "/api/v1/auth/refresh/revoke",
    "/api/v1/meetings/upcoming",
    "/api/v1/meetings/*/access-token",
    "/api/v1/profile/**",
    "/api/v1/users/**"
  };

  private static final String[] ADMIN_ENDPOINTS = {
    "/api/v1/meetings/**",
    "/api/v1/rooms/**",
    "/api/v1/config-sets/**"
  };

  @Bean
  CorsConfigurationSource corsConfigurationSource(
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(frontendOrigin));
    cors.setAllowCredentials(true);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of(
      "Content-Type",
      "Idempotency-Key",
      "X-CSRF-TOKEN",
      "X-XSRF-TOKEN",
      "X-Trace-Id"));

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AuthenticationEntryPoint jsonAuthenticationEntryPoint,
      AccessDeniedHandler jsonAccessDeniedHandler,
      ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
      @Value("${app.frontend.origin:http://localhost:3000}") String frontendOrigin,
      OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService,
      OidcLoginFailureHandler oidcLoginFailureHandler,
      OidcLoginSuccessHandler oidcLoginSuccessHandler) throws Exception {
    http.cors(Customizer.withDefaults());
    http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
    http.headers(headers -> headers
      .contentTypeOptions(Customizer.withDefaults())
      .frameOptions(frameOptions -> frameOptions.deny())
      .referrerPolicy(referrerPolicy ->
        referrerPolicy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
      .contentSecurityPolicy(contentSecurityPolicy ->
        contentSecurityPolicy.policyDirectives(BASELINE_CSP))
      .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", PERMISSIONS_POLICY)));
    http.requestCache(cache -> cache.disable());
    http.authorizeHttpRequests(auth -> auth
        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
        .requestMatchers(AUTHENTICATED_ENDPOINTS).authenticated()
        .requestMatchers(ADMIN_ENDPOINTS).hasRole("admin")
        .anyRequest().denyAll());
    http.exceptionHandling(ex -> ex
        .authenticationEntryPoint(jsonAuthenticationEntryPoint)
        .accessDeniedHandler(jsonAccessDeniedHandler));

    HttpSecurity configured = http;

    if (clientRegistrationRepositoryProvider.getIfAvailable() != null) {
      configured.oauth2Login(oauth -> oauth
          .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
          .successHandler(oidcLoginSuccessHandler)
          .failureHandler(oidcLoginFailureHandler));
    }

    return buildFilterChain(configured);
  }

  private SecurityFilterChain buildFilterChain(HttpSecurity configured) throws Exception {
    return configured.build();
  }
}
