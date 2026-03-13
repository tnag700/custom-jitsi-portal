package com.acme.jitsi.domains.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.TestDomainModuleApplication;
import com.acme.jitsi.domains.meetings.service.MeetingTokenIssuer;
import com.acme.jitsi.security.DefaultJwtAlgorithmPolicy;
import com.acme.jitsi.security.JwtAlgorithmPolicy;
import com.acme.jitsi.security.ProblemDetailsFactory;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import com.acme.jitsi.security.TenantAccessGuard;
import com.acme.jitsi.security.TokenIssuanceCompatibilityPolicy;
import com.acme.jitsi.support.MeetingsModuleScaffoldingMocksSupport;
import com.acme.jitsi.shared.observability.FlowObservationFacade;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ApplicationModuleTest(
  mode = BootstrapMode.DIRECT_DEPENDENCIES,
  classes = TestDomainModuleApplication.class,
  verifyAutomatically = false)
@SpringBootTest(classes = {
  TestDomainModuleApplication.class,
  AuthDirectDependenciesModuleIntegrationTest.ModuleTestConfig.class
}, properties = {
    "spring.main.web-application-type=none",
    "app.auth.refresh.atomic-store=in-memory",
    "app.auth.refresh.idle-ttl-minutes=60",
  "app.meetings.token.signing-secret=01234567890123456789012345678901",
    "app.meetings.token.issuer=https://portal.example.test",
    "app.meetings.token.audience=jitsi-meet",
  "app.meetings.token.algorithm=HS256",
  "app.meetings.token.ttl-minutes=20",
  "app.meetings.token.role-claim-name=role",
  "app.meetings.token.join-url-template=https://meet.example/%s#jwt=%s"
})
@Tag("integration")
class AuthDirectDependenciesModuleIntegrationTest extends MeetingsModuleScaffoldingMocksSupport {

  private static final String SECRET = "01234567890123456789012345678901";

  private final AuthRefreshService authRefreshService;
  private final JwtEncoder jwtEncoder;
  private final ConfigurableApplicationContext applicationContext;

  @MockitoBean
  private ProblemDetailsFactory problemDetailsFactory;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private TenantAccessGuard tenantAccessGuard;

  @MockitoBean
  private MeetingTokenIssuer meetingTokenIssuer;

  @BeforeEach
  void resetMocks() {
    Mockito.reset(
        problemDetailsFactory,
        problemDetailsMappingPolicy,
        problemResponseFacade,
        tenantAccessGuard,
        meetingTokenIssuer,
        userProfileService,
        roomService,
        configSetValidator);
  }

  AuthDirectDependenciesModuleIntegrationTest(
      AuthRefreshService authRefreshService,
      JwtEncoder jwtEncoder,
      ConfigurableApplicationContext applicationContext) {
    this.authRefreshService = authRefreshService;
    this.jwtEncoder = jwtEncoder;
    this.applicationContext = applicationContext;
  }

  @Test
  void refreshesSessionUsingAllowedMeetingsServiceCollaboratorAndStoreBoundary() {
    when(meetingTokenIssuer.issueAccessToken("meeting-a", "u-host"))
      .thenReturn(new MeetingTokenIssuer.AccessTokenResult(
        "issued-access-token",
        Instant.now().plus(20, ChronoUnit.MINUTES),
        "host"));

    Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    String refreshToken = buildRefreshToken(
        "refresh-jti-1",
        "u-host",
        "meeting-a",
      now.minus(5, ChronoUnit.MINUTES),
      now.plus(2, ChronoUnit.HOURS));

    AuthRefreshService.RefreshResult refreshed = authRefreshService.refresh(refreshToken);

    assertThat(refreshed.accessToken()).isEqualTo("issued-access-token");
    assertThat(refreshed.role()).isEqualTo("host");
    assertThat(refreshed.tokenType()).isEqualTo("Bearer");
    assertThat(refreshed.refreshToken()).isNotBlank();
    assertNoBeansFromPackages(
      "com.acme.jitsi.domains.health",
      "com.acme.jitsi.domains.invites");
  }

  private String buildRefreshToken(
      String tokenId,
      String subject,
      String meetingId,
      Instant issuedAt,
      Instant expiresAt) {
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("https://portal.example.test")
        .audience(List.of("jitsi-meet"))
        .subject(subject)
        .issuedAt(issuedAt)
        .expiresAt(expiresAt)
        .id(tokenId)
        .claim("tokenType", "refresh")
        .claim("meetingId", meetingId)
        .build();

    return jwtEncoder.encode(JwtEncoderParameters.from(
        JwsHeader.with(MacAlgorithm.HS256).build(),
        claims)).getTokenValue();
  }

    private void assertNoBeansFromPackages(String... packagePrefixes) {
    assertThat(Arrays.stream(applicationContext.getBeanDefinitionNames())
        .map(applicationContext::getType)
        .filter(Objects::nonNull)
        .map(Class::getName)
      .filter(name -> Arrays.stream(packagePrefixes).anyMatch(name::startsWith))
        .toList()).isEmpty();
  }

  @TestConfiguration
  static class ModuleTestConfig {
    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    JwtAlgorithmPolicy jwtAlgorithmPolicy() {
      return new DefaultJwtAlgorithmPolicy();
    }

    @Bean
    @Primary
    JwtEncoder jwtEncoder() {
      SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder() {
      SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    TokenIssuanceCompatibilityPolicy tokenIssuanceCompatibilityPolicy() {
      return Mockito.mock(TokenIssuanceCompatibilityPolicy.class);
    }

    @Bean
    FlowObservationFacade flowObservationFacade() {
      return FlowObservationFacade.noop();
    }

    @Bean
    SimpleMeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}