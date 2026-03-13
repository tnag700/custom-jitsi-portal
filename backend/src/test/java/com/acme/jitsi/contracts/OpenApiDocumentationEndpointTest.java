package com.acme.jitsi.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.shared.JwtTestProperties;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:testdb-openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class OpenApiDocumentationEndpointTest {

  @Autowired
  private OpenApiWebMvcResource openApiResource;

  @Test
  void generatedOpenApiExposesVersionedApplicationEndpointsOnly() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v3/api-docs");
    request.setScheme("http");
    request.setServerName("localhost");
    request.setServerPort(8080);
    request.setRequestURI("/v3/api-docs");
    request.setServletPath("/v3/api-docs");
    request.setContextPath("");
    String response = new String(
      openApiResource.openapiJson(request, "/v3/api-docs", Locale.ROOT),
      StandardCharsets.UTF_8);

    assertThat(response).contains("\"/api/v1/health\"");
    assertThat(response).contains("\"/api/v1/rooms\"");
    assertThat(response).doesNotContain("/actuator/health");
  }
}