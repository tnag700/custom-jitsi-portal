package com.acme.jitsi.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
class OidcRoleAuthoritiesMapper {

  Set<GrantedAuthority> mapAuthorities(OidcUser user) {
    return mapAuthorities(user, Map.of());
  }

  Set<GrantedAuthority> mapAuthorities(OidcUser user, Map<String, Object> additionalClaims) {
    LinkedHashSet<GrantedAuthority> mapped = new LinkedHashSet<>(user.getAuthorities());
    mapped.addAll(mapRealmRoles(user.getClaims()));
    mapped.addAll(mapClientRoles(user.getClaims()));
    mapped.addAll(mapTopLevelRoles(user.getClaims()));
    mapped.addAll(mapRealmRoles(additionalClaims));
    mapped.addAll(mapClientRoles(additionalClaims));
    mapped.addAll(mapTopLevelRoles(additionalClaims));
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

  private Set<GrantedAuthority> mapClientRoles(Map<String, Object> claims) {
    Object resourceAccessRaw = claims.get("resource_access");
    if (!(resourceAccessRaw instanceof Map<?, ?> resourceAccess)) {
      return Set.of();
    }

    LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (Object clientAccessRaw : resourceAccess.values()) {
      if (!(clientAccessRaw instanceof Map<?, ?> clientAccess)) {
        continue;
      }
      authorities.addAll(mapRolesCollection(clientAccess.get("roles")));
    }
    return authorities;
  }

  private Set<GrantedAuthority> mapTopLevelRoles(Map<String, Object> claims) {
    Object rolesRaw = claims.get("roles");
    return mapRolesCollection(rolesRaw);
  }

  private Set<GrantedAuthority> mapRolesCollection(Object rolesRaw) {
    if (!(rolesRaw instanceof Collection<?> roles)) {
      return Set.of();
    }

    LinkedHashSet<GrantedAuthority> authorities = new LinkedHashSet<>();
    for (Object role : roles) {
      if (!(role instanceof String roleName)) {
        continue;
      }
      String normalized = roleName.trim();
      if (normalized.isEmpty()) {
        continue;
      }
      authorities.add(new SimpleGrantedAuthority("ROLE_" + normalized));
    }
    authorities.removeIf(Objects::isNull);
    return authorities;
  }
}