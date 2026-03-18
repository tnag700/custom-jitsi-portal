package com.acme.jitsi.security;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.shared.JwtTestProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
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
class AuthMeEndpointTest {

  @LocalServerPort
  private int port;

  private final RestTemplate restTemplate = createRestTemplate();

  private RestTemplate createRestTemplate() {
    RestTemplate template = new RestTemplate();
    template.setErrorHandler(new DefaultResponseErrorHandler() {
      @Override
      public boolean hasError(ClientHttpResponse response) {
        return false;
      }
    });
    return template;
  }

  @Test
  void unauthenticatedRequestToAuthMeReturns401WithSafeErrorPayload() {
    ResponseEntity<Map<String, Object>> response = getForMap("/api/v1/auth/me");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    Map<String, Object> properties = castMap(response.getBody().get("properties"));
    assertThat(properties).isNotNull();
    assertThat(properties.get("errorCode")).isEqualTo(ErrorCode.AUTH_REQUIRED.code());
    assertThat((String) properties.get("traceId")).isNotBlank();
    assertThat((String) response.getBody().get("title")).isNotBlank();
    assertThat((String) response.getBody().get("detail")).isNotBlank();
    assertThat(response.getBody().toString()).doesNotContain("Exception");
  }

  @Test
  void directErrorEndpointIsNotBlockedBySecurityAsAccessDenied() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/error",
        String.class);

    assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
  }

  private ResponseEntity<Map<String, Object>> getForMap(String path) {
    return restTemplate.exchange(
        "http://localhost:" + port + path,
        HttpMethod.GET,
        HttpEntity.EMPTY,
        new ParameterizedTypeReference<>() {
        });
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> castMap(Object value) {
    return (Map<String, Object>) value;
  }
}


