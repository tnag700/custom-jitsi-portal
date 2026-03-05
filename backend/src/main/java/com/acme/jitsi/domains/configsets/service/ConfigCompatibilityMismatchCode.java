package com.acme.jitsi.domains.configsets.service;

public enum ConfigCompatibilityMismatchCode {
  ISSUER_MISMATCH,
  AUDIENCE_MISMATCH,
  ROLE_CLAIM_MISMATCH,
  ALGORITHM_KEY_SOURCE_MISMATCH,
  ENDPOINT_MISMATCH,
  API_VERSION_MISMATCH
}