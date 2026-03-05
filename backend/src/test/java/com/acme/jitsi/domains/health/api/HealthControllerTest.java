package com.acme.jitsi.domains.health.api;

import static org.assertj.core.api.Assertions.assertThat;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ALGORITHM;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_AUDIENCE;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ISSUER;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES;
import static com.acme.jitsi.shared.JwtTestProperties.CONTOUR_ROLE_CLAIM;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_ALGORITHM;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_AUDIENCE;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_ISSUER;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_ROLE_CLAIM_NAME;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_SIGNING_SECRET;
import static com.acme.jitsi.shared.JwtTestProperties.TOKEN_TTL_MINUTES;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    TOKEN_SIGNING_SECRET,
    TOKEN_ISSUER,
    TOKEN_AUDIENCE,
    TOKEN_ALGORITHM,
    TOKEN_TTL_MINUTES,
    TOKEN_ROLE_CLAIM_NAME,
    CONTOUR_ISSUER,
    CONTOUR_AUDIENCE,
    CONTOUR_ROLE_CLAIM,
    CONTOUR_ALGORITHM,
    CONTOUR_ACCESS_TTL_MINUTES,
    CONTOUR_REFRESH_TTL_MINUTES
  })
class HealthControllerTest {

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
  void healthEndpointReturnsUp() {
    ResponseEntity<Map> response = restTemplate.getForEntity(
      "http://localhost:" + port + "/api/v1/health",
      Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("status")).isEqualTo("UP");
  }

  @Test
  void healthEndpointRejectsPost() {
    ResponseEntity<String> response = restTemplate.exchange(
        "http://localhost:" + port + "/api/v1/health",
        HttpMethod.POST,
        null,
        String.class);

    assertThat(response.getStatusCode()).isIn(
        HttpStatus.METHOD_NOT_ALLOWED,
        HttpStatus.FORBIDDEN,
        HttpStatus.UNAUTHORIZED);
  }
}
