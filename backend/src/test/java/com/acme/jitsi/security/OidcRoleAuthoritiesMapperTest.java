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

  @Test
  void mapsRealmRolesToSpringRoleAuthorities() {
    OidcUser user = oidcUser(
        Map.of(
            "sub", "u-1",
            "realm_access", Map.of("roles", List.of("admin", "user"))));

    Set<String> authorities = mapper.mapAuthorities(user).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities).contains("ROLE_admin", "ROLE_user");
  }

  @Test
  void mapsClientRolesFromResourceAccess() {
    OidcUser user = oidcUser(
        Map.of(
            "sub", "u-1",
            "resource_access", Map.of(
                "jitsi-backend", Map.of("roles", List.of("admin")),
                "account", Map.of("roles", List.of("manage-account")))));

    Set<String> authorities = mapper.mapAuthorities(user).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities).contains("ROLE_admin", "ROLE_manage-account");
  }

  @Test
  void keepsExistingAuthoritiesAndIgnoresMissingRoleClaims() {
    OidcUser user = oidcUser(Map.of("sub", "u-1"));

    Set<String> authorities = mapper.mapAuthorities(user).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities).contains("OIDC_USER");
    assertThat(authorities).doesNotContain("ROLE_admin");
  }

  @Test
  void mapsRolesFromAdditionalClaimsWhenUserClaimsDoNotContainRoles() {
    OidcUser user = oidcUser(Map.of("sub", "u-1"));

    Set<String> authorities = mapper.mapAuthorities(
            user,
            Map.of("realm_access", Map.of("roles", List.of("admin", "user"))))
        .stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities).contains("ROLE_admin", "ROLE_user");
  }

  @Test
  void mapsTopLevelRolesClaim() {
    OidcUser user = oidcUser(Map.of("sub", "u-1", "roles", List.of("admin")));

    Set<String> authorities = mapper.mapAuthorities(user).stream()
        .map(GrantedAuthority::getAuthority)
        .collect(java.util.stream.Collectors.toSet());

    assertThat(authorities).contains("ROLE_admin");
  }

  private OidcUser oidcUser(Map<String, Object> claims) {
    OidcIdToken idToken = new OidcIdToken(
        "token-value",
        Instant.now().minusSeconds(5),
        Instant.now().plusSeconds(300),
        claims);
    return new DefaultOidcUser(List.of(() -> "OIDC_USER"), idToken);
  }
}