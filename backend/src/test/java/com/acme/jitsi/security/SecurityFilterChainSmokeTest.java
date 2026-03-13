package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.shared.JwtTestProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      JwtTestProperties.TOKEN_ISSUER,
      JwtTestProperties.TOKEN_AUDIENCE,
      JwtTestProperties.TOKEN_ROLE_CLAIM_NAME,
      JwtTestProperties.TOKEN_ALGORITHM,
      JwtTestProperties.TOKEN_TTL_MINUTES,
      JwtTestProperties.TOKEN_SIGNING_SECRET,
      "app.auth.refresh.idle-ttl-minutes=60",
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES
    })
class SecurityFilterChainSmokeTest {

  @Autowired
  private SecurityFilterChain securityFilterChain;

  @Autowired
  private AuthenticationEntryPoint authenticationEntryPoint;

  @Autowired
  private AccessDeniedHandler accessDeniedHandler;

  @Autowired
  private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;

  @Autowired
  private OidcLoginFailureHandler oidcLoginFailureHandler;

  @Test
  void securityFilterChainBeanIsCreatedWithFocusedCollaborators() {
    assertThat(securityFilterChain).isNotNull();
    assertThat(authenticationEntryPoint).isInstanceOf(JsonSecurityAuthenticationEntryPoint.class);
    assertThat(accessDeniedHandler).isInstanceOf(JsonSecurityAccessDeniedHandler.class);
    assertThat(oidcUserService).isInstanceOf(OidcUserEnrichmentService.class);
    assertThat(oidcLoginFailureHandler).isInstanceOf(OidcLoginFailureHandler.class);
  }
}
