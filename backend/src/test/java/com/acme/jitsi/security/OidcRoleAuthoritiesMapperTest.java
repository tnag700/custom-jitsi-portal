package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OidcRoleAuthoritiesMapperTest {

  private final OidcRoleAuthoritiesMapper mapper = new OidcRoleAuthoritiesMapper();
  private static final String PORTAL_CLIENT_ID = "jitsi-backend";

  @Test
  void mapsOnlyKnownRealmRolesToSpringRoleAuthorities() {
    OidcUser user = oidcUser(
        Map.of(
            "sub", "u-1",
            "realm_access", Map.of("roles", List.of("admin", "user", "offline_access"))));

    Set<String> authorities = mapper.mapAuthorities(user, PORTAL_CLIENT_ID).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities)
        .contains("ROLE_admin")
        .doesNotContain("ROLE_user", "ROLE_offline_access");
  }

  @Test
  void mapsKnownRolesOnlyFromConfiguredClientResourceAccess() {
    OidcUser user = oidcUser(
        Map.of(
            "sub", "u-1",
            "resource_access", Map.of(
                PORTAL_CLIENT_ID, Map.of("roles", List.of("participant")),
                "other-client", Map.of("roles", List.of("admin")),
                "account", Map.of("roles", List.of("manage-account")))));

    Set<String> authorities = mapper.mapAuthorities(user, PORTAL_CLIENT_ID).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities)
        .contains("ROLE_participant")
        .doesNotContain("ROLE_admin", "ROLE_manage-account");
  }

  @Test
  void preservesNonRoleAuthoritiesButRejectsPreMappedRoleAuthorities() {
    OidcUser user = oidcUser(
        Map.of("sub", "u-1"),
        List.of(() -> "OIDC_USER", () -> "SCOPE_openid", () -> "ROLE_admin"));

    Set<String> authorities = mapper.mapAuthorities(user, PORTAL_CLIENT_ID).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities)
        .contains("OIDC_USER", "SCOPE_openid")
        .doesNotContain("ROLE_admin");
  }

  @Test
  void mapsRolesFromAdditionalClaimsWhenUserClaimsDoNotContainRoles() {
    OidcUser user = oidcUser(Map.of("sub", "u-1"));

    Set<String> authorities = mapper.mapAuthorities(
            user,
            Map.of("realm_access", Map.of("roles", List.of("admin", "user"))),
            PORTAL_CLIENT_ID)
        .stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities)
        .contains("ROLE_admin")
        .doesNotContain("ROLE_user");
  }

  @Test
  void ignoresAmbiguousTopLevelRolesClaim() {
    OidcUser user = oidcUser(Map.of("sub", "u-1", "roles", List.of("admin")));

    Set<String> authorities = mapper.mapAuthorities(user, PORTAL_CLIENT_ID).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities).doesNotContain("ROLE_admin");
  }

  private OidcUser oidcUser(Map<String, Object> claims) {
    return oidcUser(claims, List.of(() -> "OIDC_USER"));
  }

  private OidcUser oidcUser(
      Map<String, Object> claims,
      List<GrantedAuthority> authorities) {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(5),
        Instant.now().plusSeconds(300),
        claims);
    return new DefaultOidcUser(authorities, idToken);
  }
}
