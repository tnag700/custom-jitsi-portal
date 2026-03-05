package com.acme.jitsi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

class JwtAlgorithmPolicyTest {

  private final JwtAlgorithmPolicy policy = new DefaultJwtAlgorithmPolicy();

  @Test
  void resolvesMacAlgorithmForSecretWithNormalization() {
    assertThat(policy.resolveMacAlgorithmForSecret(" hs256 ")).isEqualTo(MacAlgorithm.HS256);
    assertThat(policy.resolveMacAlgorithmForSecret("Hs384")).isEqualTo(MacAlgorithm.HS384);
    assertThat(policy.resolveMacAlgorithmForSecret("HS512")).isEqualTo(MacAlgorithm.HS512);
  }

  @Test
  void resolvesJcaAlgorithmForSecretWithNormalization() {
    assertThat(policy.resolveJcaAlgorithmForSecret(" hs256 ")).isEqualTo("HmacSHA256");
    assertThat(policy.resolveJcaAlgorithmForSecret("Hs384")).isEqualTo("HmacSHA384");
    assertThat(policy.resolveJcaAlgorithmForSecret("HS512")).isEqualTo("HmacSHA512");
  }

  @Test
  void rejectsUnsupportedAlgorithmsForSecretMapping() {
    assertThatThrownBy(() -> policy.resolveMacAlgorithmForSecret("RS256"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported JWT MAC algorithm");

    assertThatThrownBy(() -> policy.resolveJcaAlgorithmForSecret("ES256"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported JWT MAC algorithm");
  }

  @Test
  void checksSupportedAlgorithmsByKeySource() {
    assertThat(policy.isSupportedForKeySource("HS256", JwtKeySource.SECRET)).isTrue();
    assertThat(policy.isSupportedForKeySource("HS384", JwtKeySource.SECRET)).isTrue();
    assertThat(policy.isSupportedForKeySource("HS512", JwtKeySource.SECRET)).isTrue();
    assertThat(policy.isSupportedForKeySource("RS256", JwtKeySource.SECRET)).isFalse();

    assertThat(policy.isSupportedForKeySource("RS256", JwtKeySource.JWKS)).isFalse();
    assertThat(policy.isSupportedForKeySource("ES256", JwtKeySource.JWKS)).isFalse();
    assertThat(policy.isSupportedForKeySource("HS256", JwtKeySource.JWKS)).isFalse();
  }

  @Test
  void exposesSupportedAlgorithmsByKeySource() {
    assertThat(policy.supportedAlgorithmsForKeySource(JwtKeySource.SECRET)).containsExactlyInAnyOrder("HS256", "HS384", "HS512");
    assertThat(policy.supportedAlgorithmsForKeySource(JwtKeySource.JWKS)).isEmpty();
  }
}