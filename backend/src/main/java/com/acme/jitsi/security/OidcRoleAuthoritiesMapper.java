package com.acme.jitsi.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
class OidcRoleAuthoritiesMapper {

  Set<GrantedAuthority> mapAuthorities(OidcUser user, String clientId) {
    return mapAuthorities(user, Map.of(), clientId);
  }

  Set<GrantedAuthority> mapAuthorities(
      OidcUser user,
      Map<String, Object> additionalClaims,
      String clientId) {
    Objects.requireNonNull(clientId, "clientId");

    LinkedHashSet<GrantedAuthority> mapped = new LinkedHashSet<>();
    user.getAuthorities().stream()
        .filter(authority -> !authority.getAuthority().startsWith("ROLE_"))
        .forEach(mapped::add);
    mapped.addAll(mapRealmRoles(user.getClaims()));
    mapped.addAll(mapClientRoles(user.getClaims(), clientId));
    mapped.addAll(mapRealmRoles(additionalClaims));
    mapped.addAll(mapClientRoles(additionalClaims, clientId));
    return mapped;
  }

  private Set<GrantedAuthority> mapRealmRoles(Map<String, Object> claims) {
    Object realmAccessRaw = claims.get("realm_access");
    if (!(realmAccessRaw instanceof Map<?, ?> realmAccess)) {
      return Set.of();
    }

    Object rolesRaw = realmAccess.get("roles");
    return mapRolesCollection(rolesRaw);
  }

  private Set<GrantedAuthority> mapClientRoles(
      Map<String, Object> claims,
      String clientId) {
    Object resourceAccessRaw = claims.get("resource_access");
    if (!(resourceAccessRaw instanceof Map<?, ?> resourceAccess)) {
      return Set.of();
    }

    Object clientAccessRaw = resourceAccess.get(clientId);
    if (!(clientAccessRaw instanceof Map<?, ?> clientAccess)) {
      return Set.of();
    }

    return mapRolesCollection(clientAccess.get("roles"));
  }

  private Set<GrantedAuthority> mapRolesCollection(Object rolesRaw) {
    if (!(rolesRaw instanceof Collection<?> roles)) {
      return Set.of();
    }

    LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (Object role : roles) {
      PortalRole.fromClaim(role)
          .map(PortalRole::authority)
          .ifPresent(authorities::add);
    }
    authorities.removeIf(Objects::isNull);
    return authorities;
  }
}
