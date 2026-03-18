package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class SecurityConfigOidcUserServiceCharacterizationTest {

  private final OidcClaimsValidator claimsValidator = mock(OidcClaimsValidator.class);
  private final OidcRoleAuthoritiesMapper roleAuthoritiesMapper = mock(OidcRoleAuthoritiesMapper.class);
  private final OidcUserService delegate = mock(OidcUserService.class);
  private final OidcUserEnrichmentService oidcUserEnrichmentService = new OidcUserEnrichmentService(
      claimsValidator,
      roleAuthoritiesMapper,
      new OidcAccessTokenClaimsExtractor(),
      "https://issuer.example.test",
      "jitsi-backend",
      delegate);

  @Test
  void oidcUserServiceMergesUserInfoAndAccessTokenClaimsForSuccessfulLogin() {
    OidcUser delegateUser = delegateUser(Map.of("email", "user@example.test", "locale", "ru"));
    when(roleAuthoritiesMapper.mapAuthorities(any(OidcUser.class), anyMap()))
        .thenReturn(Set.of(new SimpleGrantedAuthority("ROLE_admin")));

    when(delegate.loadUser(any(OidcUserRequest.class))).thenReturn(delegateUser);
    OAuth2UserService<OidcUserRequest, OidcUser> service = oidcUserEnrichmentService;

    OidcUser user = service.loadUser(oidcUserRequest(signedTokenWithRoles()));

    verify(claimsValidator).validate(
      any(OidcIdToken.class),
      eq("https://issuer.example.test"),
      eq("jitsi-backend"));
    ArgumentCaptor<Map<String, Object>> claimsCaptor = mapCaptor();
    verify(roleAuthoritiesMapper).mapAuthorities(eq(delegateUser), claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
      .containsEntry("roles", List.of("admin"))
      .containsEntry("tenantId", "tenant-1");
    assertThat(user.getUserInfo().getClaims())
      .containsEntry("email", "user@example.test")
      .containsEntry("locale", "ru")
      .containsEntry("tenantId", "tenant-1")
      .containsEntry("roles", List.of("admin"));
    assertThat(user.getAuthorities())
      .extracting(GrantedAuthority::getAuthority)
      .containsExactly("ROLE_admin");
  }

  @Test
  void oidcUserServiceFallsBackToEmptyAccessTokenClaimsWhenParsingFails() {
    OidcUser delegateUser = delegateUser(Map.of("email", "user@example.test"));
    when(roleAuthoritiesMapper.mapAuthorities(any(OidcUser.class), anyMap()))
        .thenReturn(Set.of());

    when(delegate.loadUser(any(OidcUserRequest.class))).thenReturn(delegateUser);
    OAuth2UserService<OidcUserRequest, OidcUser> service = oidcUserEnrichmentService;

    OidcUser user = service.loadUser(oidcUserRequest("not-a-jwt"));

    ArgumentCaptor<Map<String, Object>> claimsCaptor = mapCaptor();
    verify(roleAuthoritiesMapper).mapAuthorities(eq(delegateUser), claimsCaptor.capture());
    assertThat(claimsCaptor.getValue()).isEmpty();
    assertThat(user.getUserInfo().getClaims()).containsOnlyKeys("email");
    assertThat(user.getUserInfo().getClaimAsString("email")).isEqualTo("user@example.test");
  }

  @Test
  void oidcUserServicePropagatesValidationFailureForLoginFailurePath() {
    OidcUser delegateUser = delegateUser(Map.of("email", "user@example.test"));
    OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
        new OAuth2Error("invalid_token", "issuer mismatch", null),
        "issuer mismatch");
    doThrow(exception).when(claimsValidator).validate(any(OidcIdToken.class), any(String.class), any(String.class));

    when(delegate.loadUser(any(OidcUserRequest.class))).thenReturn(delegateUser);
    OAuth2UserService<OidcUserRequest, OidcUser> service = oidcUserEnrichmentService;

    assertThatThrownBy(() -> service.loadUser(oidcUserRequest(signedTokenWithRoles())))
        .isSameAs(exception);
  }

  private OidcUser delegateUser(Map<String, Object> userInfoClaims) {
    OidcIdToken idToken = new OidcIdToken(
        "id-token-value",
        Instant.now().minusSeconds(5),
        Instant.now().plusSeconds(300),
        Map.of(
            "sub", "user-1",
            "iss", "https://issuer.example.test",
            "aud", List.of("jitsi-backend")));
    return new DefaultOidcUser(
        Set.of(new SimpleGrantedAuthority("OIDC_USER")),
        idToken,
        new OidcUserInfo(userInfoClaims));
  }

  private OidcUserRequest oidcUserRequest(String accessTokenValue) {
    ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
        .clientId("jitsi-backend")
        .clientSecret("secret")
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .redirectUri("http://localhost/login/oauth2/code/keycloak")
        .authorizationUri("https://issuer.example.test/auth")
        .tokenUri("https://issuer.example.test/token")
        .userInfoUri("https://issuer.example.test/userinfo")
        .userNameAttributeName("sub")
        .jwkSetUri("https://issuer.example.test/jwks")
        .issuerUri("https://issuer.example.test")
        .scope("openid", "profile", "email")
        .build();

    OidcIdToken idToken = new OidcIdToken(
        "id-token-value",
        Instant.now().minusSeconds(5),
        Instant.now().plusSeconds(300),
        Map.of(
            "sub", "user-1",
            "iss", "https://issuer.example.test",
            "aud", List.of("jitsi-backend")));
    OAuth2AccessToken accessToken = new OAuth2AccessToken(
        OAuth2AccessToken.TokenType.BEARER,
        accessTokenValue,
        Instant.now().minusSeconds(5),
        Instant.now().plusSeconds(300));
    return new OidcUserRequest(registration, accessToken, idToken);
  }

  private String signedTokenWithRoles() {
    return "eyJhbGciOiJIUzI1NiJ9.eyJ0ZW5hbnRJZCI6InRlbmFudC0xIiwicm9sZXMiOlsiYWRtaW4iXX0.c2lnbmF0dXJl";
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Map<String, Object>> mapCaptor() {
    return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
  }
}