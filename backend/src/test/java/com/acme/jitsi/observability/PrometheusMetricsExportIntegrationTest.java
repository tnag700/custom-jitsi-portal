package com.acme.jitsi.observability;

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
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@Tag("integration")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:prometheus-export;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.db.enabled=false",
      "management.health.redis.enabled=false",
      "app.features.advanced-monitoring=true",
      "management.endpoints.web.exposure.include=health,info,prometheus",
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
class PrometheusMetricsExportIntegrationTest {

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
  void actuatorPrometheusExportsCanonicalPhaseOneMetrics() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/actuator/prometheus",
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotBlank();
    assertThat(response.getBody()).contains("jitsi_join_attempts_total");
    assertThat(response.getBody()).contains("jitsi_join_latency_seconds");
    assertThat(response.getBody()).contains("jitsi_service_backend_available");
    assertThat(response.getBody()).contains("jitsi_service_config_compatible");
  }

  @Test
  void actuatorPrometheusRequiresExplicitExposureEvenWhenFeatureIsEnabled() {
    ResponseEntity<String> response = restTemplate.getForEntity(
        "http://localhost:" + port + "/actuator/env",
        String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}