package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionInformation;
import org.springframework.security.oauth2.client.oidc.session.OidcSessionRegistry;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:h2:mem:oidc-backchannel;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "management.health.redis.enabled=false",
    "app.security.sso.expected-issuer=https://issuer.example.test",
    "spring.security.oauth2.client.registration.keycloak.client-id=jitsi-backend",
    "spring.security.oauth2.client.registration.keycloak.client-secret=test-secret",
    "spring.security.oauth2.client.registration.keycloak.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.keycloak.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.registration.keycloak.scope=openid",
    "spring.security.oauth2.client.provider.keycloak.authorization-uri=https://issuer.example.test/auth",
    "spring.security.oauth2.client.provider.keycloak.token-uri=https://issuer.example.test/token",
    "spring.security.oauth2.client.provider.keycloak.user-info-uri=https://issuer.example.test/userinfo",
    "spring.security.oauth2.client.provider.keycloak.user-name-attribute=sub"
})
@Import({OidcBackchannelIntegrationTest.SessionController.class, OidcBackchannelIntegrationTest.SessionConfig.class})
class OidcBackchannelIntegrationTest {
    private static final String ISSUER = "https://issuer.example.test";
    private static final RSAKey KEY;
    private static final HttpServer JWKS;
    static {
        try {
            KEY = new RSAKeyGenerator(2048).keyID("test-oidc").generate();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            JWKS.start();
        } catch (Exception exception) { throw new ExceptionInInitializerError(exception); }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.security.oauth2.client.provider.keycloak.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
    }

    @AfterAll
    static void stopJwks() { JWKS.stop(0); }

    @LocalServerPort
    private int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void malformedLogoutReachesTokenValidationWithoutCsrfOrSession() throws Exception {
        assertThat(logout("malformed")).isEqualTo(400);
    }

    @Test
    void signedLogoutInvalidatesOnlyMatchingSessionAndReplayIsHarmless() throws Exception {
        String alice = login("alice", "sid-alice");
        String bob = login("bob", "sid-bob");
        assertThat(me(alice)).isEqualTo(200);
        String token = token(KEY, ISSUER, "jitsi-backend", "alice", "sid-alice");
        assertThat(logout(token)).isEqualTo(200);
        assertThat(me(alice)).isEqualTo(401);
        assertThat(me(bob)).isEqualTo(200);
        assertThat(logout(token)).isEqualTo(200);
    }

    @Test
    void subjectLogoutRevokesEverySessionOfThatUser() throws Exception {
        String first = login("subject-user", "sid-one");
        String second = login("subject-user", "sid-two");
        assertThat(logout(token(KEY, ISSUER, "jitsi-backend", "subject-user", null))).isEqualTo(200);
        assertThat(me(first)).isEqualTo(401);
        assertThat(me(second)).isEqualTo(401);
    }

    @Test
    void wrongIssuerAudienceAndSignatureCannotRevokeTheSession() throws Exception {
        String cookie = login("protected-user", "protected-sid");
        assertThat(logout(token(KEY, "https://wrong.example.test", "jitsi-backend", "protected-user", null))).isEqualTo(400);
        assertThat(logout(token(KEY, ISSUER, "different-client", "protected-user", null))).isEqualTo(400);
        RSAKey foreign = new RSAKeyGenerator(2048).keyID(KEY.getKeyID()).generate();
        assertThat(logout(token(foreign, ISSUER, "jitsi-backend", "protected-user", null))).isEqualTo(400);
        assertThat(me(cookie)).isEqualTo(200);
    }

    private String login(String subject, String sid) throws Exception {
        var result = client.send(HttpRequest.newBuilder(uri("/api/v1/test/oidc-session?subject=" + subject + "&sid=" + sid)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200);
        return result.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
    }

    private int me(String cookie) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/v1/auth/me")).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private int logout(String token) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/logout/connect/back-channel/keycloak"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("logout_token=" + URLEncoder.encode(token, StandardCharsets.UTF_8))).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }

    private static String token(RSAKey key, String issuer, String audience, String subject, String sid) throws Exception {
        var claims = new JWTClaimsSet.Builder().issuer(issuer).audience(audience).subject(subject)
                .issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString())
                .claim("events", Map.of("http://schemas.openid.net/event/backchannel-logout", Map.of()));
        if (sid != null) { claims.claim("sid", sid); }
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).keyID(key.getKeyID()).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SessionConfig {
        @Bean
        @Order(0)
        SecurityFilterChain testSessionRoute(HttpSecurity http) throws Exception {
            return http.securityMatcher("/api/v1/test/oidc-session").authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).build();
        }
    }

    @RestController
    static class SessionController {
        @Autowired(required = false)
        private OidcSessionRegistry sessions;

        @GetMapping("/api/v1/test/oidc-session")
        String login(@RequestParam("subject") String subject, @RequestParam("sid") String sid,
                HttpServletRequest request, HttpServletResponse response) {
            var user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_admin")),
                    new OidcIdToken("id-token", Instant.now(), Instant.now().plusSeconds(300),
                            Map.of("sub", subject, "sid", sid, "iss", ISSUER, "aud", List.of("jitsi-backend"))));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new OAuth2AuthenticationToken(user, user.getAuthorities(), "keycloak"));
            new HttpSessionSecurityContextRepository().saveContext(context, request, response);
            sessions.saveSessionInformation(new OidcSessionInformation(request.getSession().getId(), Map.of(), user));
            return "ok";
        }
    }
}
