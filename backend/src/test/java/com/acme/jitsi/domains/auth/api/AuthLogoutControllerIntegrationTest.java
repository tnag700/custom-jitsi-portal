package com.acme.jitsi.domains.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb_auth_logout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "app.security.sso.expected-issuer=https://issuer.example.test",
      "app.frontend.origin=http://localhost:3000",
      JwtTestProperties.TOKEN_SIGNING_SECRET,
      JwtTestProperties.TOKEN_ISSUER,
      JwtTestProperties.TOKEN_AUDIENCE,
      JwtTestProperties.TOKEN_ALGORITHM,
      JwtTestProperties.TOKEN_TTL_MINUTES,
      JwtTestProperties.TOKEN_ROLE_CLAIM_NAME,
      "app.auth.refresh.idle-ttl-minutes=60",
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES
    })
@AutoConfigureMockMvc
class AuthLogoutControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void logoutUsesRealCookieBasedCsrfHandshake() throws Exception {
    MockHttpSession session = new MockHttpSession();

    MvcResult csrfBootstrap = mockMvc.perform(get("/api/v1/auth/csrf")
            .session(session)
            .with(authentication(oidcAuthentication("id-token-cookie-csrf", "u-cookie-csrf"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isNotEmpty())
        .andReturn();

    String csrfToken = com.jayway.jsonpath.JsonPath.parse(csrfBootstrap.getResponse().getContentAsString())
        .read("$.token", String.class);
    Cookie csrfCookie = csrfBootstrap.getResponse().getCookie("XSRF-TOKEN");

    mockMvc.perform(post("/api/v1/auth/logout")
            .session(session)
            .cookie(new MockCookie("JSESSIONID", session.getId()), csrfCookie)
            .header("X-XSRF-TOKEN", csrfToken)
            .with(authentication(oidcAuthentication("id-token-cookie-csrf", "u-cookie-csrf"))))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", containsString("protocol/openid-connect/logout")))
        .andExpect(header().string("Location", containsString("id_token_hint=id-token-cookie-csrf")))
          .andExpect(header().string("Location", containsString("post_logout_redirect_uri=http://localhost:3000/auth")))
        .andExpect(result -> {
          var cookies = result.getResponse().getHeaders("Set-Cookie");
          assertThat(cookies).anyMatch(value -> value.contains("JSESSIONID=") && value.contains("Max-Age=0"));
          assertThat(cookies).anyMatch(value -> value.contains("XSRF-TOKEN=") && value.contains("Max-Age=0"));
        });
  }

  @Test
  void logoutWithoutCsrfCookieBasedHandshakeIsRejected() throws Exception {
    MockHttpSession session = new MockHttpSession();

    mockMvc.perform(post("/api/v1/auth/logout")
            .session(session)
            .cookie(new MockCookie("JSESSIONID", session.getId()))
            .with(authentication(oidcAuthentication("id-token-no-cookie-csrf", "u-no-cookie-csrf"))))
        .andExpect(status().isForbidden());
  }

  private OAuth2AuthenticationToken oidcAuthentication(String tokenValue, String subject) {
    OidcIdToken idToken = new OidcIdToken(
        tokenValue,
        Instant.now(),
        Instant.now().plusSeconds(300),
        Map.of("sub", subject, "email", subject + "@example.test", "name", "Logout User"));
    OidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_admin")), idToken, "sub");
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak");
  }
}