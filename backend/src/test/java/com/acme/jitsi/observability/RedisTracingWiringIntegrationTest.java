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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trace-redis-wiring;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
@AutoConfigureTracing
class RedisTracingWiringIntegrationTest {

  @Autowired
  private LettuceConnectionFactory lettuceConnectionFactory;

  @Test
  void redisClientResourcesUseTracingInsteadOfNoOpImplementation() {
    assertThat(lettuceConnectionFactory).isNotNull();
    assertThat(lettuceConnectionFactory.getClientResources()).isNotNull();
    assertThat(lettuceConnectionFactory.getClientResources().tracing())
        .isNotNull()
        .extracting(tracing -> tracing.getClass().getName())
        .isNotEqualTo("io.lettuce.core.tracing.NoOpTracing");
  }
}