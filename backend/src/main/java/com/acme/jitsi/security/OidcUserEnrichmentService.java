package com.acme.jitsi.security;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
final class OidcUserEnrichmentService implements OAuth2UserService<OidcUserRequest, OidcUser> {

  private final OidcClaimsValidator claimsValidator;
  private final OidcRoleAuthoritiesMapper roleAuthoritiesMapper;
  private final OidcAccessTokenClaimsExtractor oidcAccessTokenClaimsExtractor;
  private final String expectedIssuer;
  private final String clientId;
  private final OidcUserService delegate;

  @Autowired
  OidcUserEnrichmentService(
      OidcClaimsValidator claimsValidator,
      OidcRoleAuthoritiesMapper roleAuthoritiesMapper,
      OidcAccessTokenClaimsExtractor oidcAccessTokenClaimsExtractor,
      @Value("${app.security.sso.expected-issuer:http://localhost:8081/realms/jitsi-dev}") String expectedIssuer,
      @Value("${spring.security.oauth2.client.registration.keycloak.client-id:jitsi-backend}") String clientId) {
    this(
        claimsValidator,
        roleAuthoritiesMapper,
        oidcAccessTokenClaimsExtractor,
        expectedIssuer,
        clientId,
        new OidcUserService());
  }

  OidcUserEnrichmentService(
      OidcClaimsValidator claimsValidator,
      OidcRoleAuthoritiesMapper roleAuthoritiesMapper,
      OidcAccessTokenClaimsExtractor oidcAccessTokenClaimsExtractor,
      String expectedIssuer,
      String clientId,
      OidcUserService delegate) {
    this.claimsValidator = claimsValidator;
    this.roleAuthoritiesMapper = roleAuthoritiesMapper;
    this.oidcAccessTokenClaimsExtractor = oidcAccessTokenClaimsExtractor;
    this.expectedIssuer = expectedIssuer;
    this.clientId = clientId;
    this.delegate = delegate;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser user = delegate.loadUser(userRequest);
    OidcIdToken idToken = userRequest.getIdToken();
    claimsValidator.validate(idToken, expectedIssuer, clientId);

    Map<String, Object> accessTokenClaims = oidcAccessTokenClaimsExtractor.extract(userRequest.getAccessToken().getTokenValue());
    Map<String, Object> mergedUserInfoClaims = new HashMap<>();
    if (user.getUserInfo() != null) {
      mergedUserInfoClaims.putAll(user.getUserInfo().getClaims());
    }
    mergedUserInfoClaims.putAll(accessTokenClaims);

    return new DefaultOidcUser(
        roleAuthoritiesMapper.mapAuthorities(user, accessTokenClaims),
        user.getIdToken(),
        new OidcUserInfo(mergedUserInfoClaims));
  }
}