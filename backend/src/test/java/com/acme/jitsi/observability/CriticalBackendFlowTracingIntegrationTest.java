package com.acme.jitsi.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.shared.JwtTestProperties;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:trace-critical-flows;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.jpa.hibernate.ddl-auto=validate",
      "spring.flyway.enabled=true",
      "management.health.redis.enabled=false",
      "management.opentelemetry.tracing.export.schedule-delay=10ms",
      "management.opentelemetry.tracing.export.max-batch-size=1",
      "app.security.sso.expected-issuer=https://issuer.example.test",
      JwtTestProperties.TOKEN_SIGNING_SECRET,
      JwtTestProperties.TOKEN_ISSUER,
      JwtTestProperties.TOKEN_AUDIENCE,
      JwtTestProperties.TOKEN_ALGORITHM,
      JwtTestProperties.TOKEN_TTL_MINUTES,
      JwtTestProperties.TOKEN_ROLE_CLAIM_NAME,
      JwtTestProperties.CONTOUR_ISSUER,
      JwtTestProperties.CONTOUR_AUDIENCE,
      JwtTestProperties.CONTOUR_ROLE_CLAIM,
      JwtTestProperties.CONTOUR_ALGORITHM,
      JwtTestProperties.CONTOUR_ACCESS_TTL_MINUTES,
      JwtTestProperties.CONTOUR_REFRESH_TTL_MINUTES,
      "app.meetings.token.join-url-template=https://meet.example/%s#jwt=%s",
      "app.meetings.token.known-meeting-ids=meeting-a",
      "app.meetings.token.assignments[0].meeting-id=meeting-a",
      "app.meetings.token.assignments[0].subject=u-host",
      "app.meetings.token.assignments[0].role=host",
      "app.auth.refresh.atomic-store=in-memory",
      "app.auth.refresh.idle-ttl-minutes=60",
      "app.invites.mode=properties",
      "app.invites.exchange.atomic-store=in-memory",
      "app.invites.exchange.invites[0].token=invite-valid",
      "app.invites.exchange.invites[0].meeting-id=meeting-a",
      "app.invites.exchange.invites[0].expires-at=2099-01-01T00:00:00Z",
      "app.invites.exchange.invites[0].usage-limit=10",
      "app.invites.exchange.known-meeting-ids=meeting-a"
    })
@AutoConfigureMockMvc
@AutoConfigureTracing
@Import(TestTracingConfiguration.class)
class CriticalBackendFlowTracingIntegrationTest {

  private static final String SECRET = "01234567890123456789012345678901";
  private static final AttributeKey<String> HTTP_URL = AttributeKey.stringKey("http.url");
  private static final AttributeKey<String> URI = AttributeKey.stringKey("uri");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TestSpanExporter testSpanExporter;

  @Test
  void inviteExchangeExportsCustomSpanInServerTrace() throws Exception {
    testSpanExporter.reset();

    mockMvc.perform(post("/api/v1/invites/exchange")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  \"inviteToken\": \"invite-valid\",
                  \"displayName\": \"Guest User\"
                }
                """))
        .andExpect(status().isOk());

    List<SpanData> spans = testSpanExporter.await(
        exported -> hasSpan(exported, "invite.exchange"),
        Duration.ofSeconds(5));

    SpanData customSpan = findSpan(spans, "invite.exchange");
    assertThat(spans)
        .anySatisfy(span -> assertThat(isServerSpanForPath(span, "/api/v1/invites/exchange")
            && span.getTraceId().equals(customSpan.getTraceId())).isTrue());
    assertThat(customSpan.getAttributes().get(AttributeKey.stringKey("flow.outcome"))).isEqualTo("success");
    assertThat(customSpan.getAttributes().get(AttributeKey.stringKey("flow.guest"))).isEqualTo("true");
    assertThat(customSpan.getAttributes().toString())
        .doesNotContain("invite-valid")
        .doesNotContain("Guest User");
  }

  @Test
  void authRefreshExportsCustomSpanAndCompatibilityLookupSpan() throws Exception {
    testSpanExporter.reset();

    String refreshToken = buildRefreshToken(
        "refresh-jti-1",
        "u-host",
        "meeting-a",
        Instant.now(),
        Instant.now().plus(2, ChronoUnit.HOURS));

    mockMvc.perform(post("/api/v1/auth/refresh")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{" + "\"refreshToken\":\"" + refreshToken + "\"}"))
        .andExpect(status().isOk());

    List<SpanData> spans = testSpanExporter.await(
        exported -> hasSpan(exported, "auth.refresh") && hasSpan(exported, "auth.refresh.rotation")
            && hasSpan(exported, "config.compatibility.check"),
        Duration.ofSeconds(5));

    SpanData authSpan = findSpan(spans, "auth.refresh");
    assertThat(spans)
        .anySatisfy(span -> assertThat(isServerSpanForPath(span, "/api/v1/auth/refresh")
            && span.getTraceId().equals(authSpan.getTraceId())).isTrue());
    assertThat(spans)
        .anySatisfy(span -> assertThat("auth.refresh.rotation".equals(span.getName())
            && span.getTraceId().equals(authSpan.getTraceId())).isTrue());
    assertThat(spans)
        .anySatisfy(span -> assertThat("config.compatibility.check".equals(span.getName())
            && span.getTraceId().equals(authSpan.getTraceId())).isTrue());
    assertThat(authSpan.getAttributes().toString()).doesNotContain(refreshToken).doesNotContain("u-host");
    SpanData rotationSpan = findSpan(spans, "auth.refresh.rotation");
    assertThat(rotationSpan.getAttributes().get(AttributeKey.stringKey("flow.outcome"))).isEqualTo("success");
    assertThat(rotationSpan.getAttributes().get(AttributeKey.stringKey("flow.compatibility"))).isEqualTo("compatible");
  }

  @Test
  void meetingAccessTokenExportsCustomSpanInServerTrace() throws Exception {
    testSpanExporter.reset();

    mockMvc.perform(post("/api/v1/meetings/meeting-a/access-token")
            .with(csrf())
            .with(oauth2Login()
                .attributes(attrs -> attrs.put("sub", "u-host"))
                .authorities(new SimpleGrantedAuthority("ROLE_user"))))
        .andExpect(status().isOk());

    List<SpanData> spans = testSpanExporter.await(
        exported -> hasSpan(exported, "meetings.access-token.issue") && hasSpan(exported, "config.compatibility.check"),
        Duration.ofSeconds(5));

    SpanData meetingSpan = findSpan(spans, "meetings.access-token.issue");
    assertThat(spans)
        .anySatisfy(span -> assertThat(isServerSpanForPath(span, "/api/v1/meetings/meeting-a/access-token")
            && span.getTraceId().equals(meetingSpan.getTraceId())).isTrue());
    assertThat(meetingSpan.getAttributes().get(AttributeKey.stringKey("flow.outcome"))).isEqualTo("success");
    assertThat(meetingSpan.getAttributes().toString()).doesNotContain("u-host");
  }

  private static boolean hasSpan(List<SpanData> spans, String name) {
    return spans.stream().anyMatch(span -> name.equals(span.getName()));
  }

  private static SpanData findSpan(List<SpanData> spans, String name) {
    return spans.stream()
        .filter(span -> name.equals(span.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing span: " + name));
  }

  private static boolean isServerSpanForPath(SpanData span, String path) {
    if (span.getKind() != SpanKind.SERVER) {
      return false;
    }
    String httpUrl = span.getAttributes().get(HTTP_URL);
    String uri = span.getAttributes().get(URI);
    return path.equals(httpUrl) || path.equals(uri) || (httpUrl != null && httpUrl.endsWith(path));
  }

  private String buildRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt) throws Exception {
    SignedJWT jwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.HS256)
            .type(JOSEObjectType.JWT)
            .build(),
        new JWTClaimsSet.Builder()
            .issuer("https://portal.example.test")
            .audience("jitsi-meet")
            .jwtID(tokenId)
            .subject(subject)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .claim("tokenType", "refresh")
            .claim("meetingId", meetingId)
            .build());
    jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
    return jwt.serialize();
  }
}