package com.acme.jitsi.security;

public enum JwtStartupValidationErrorCode {
  NONE,
  CONFIG_MISSING_REQUIRED,
  CONFIG_INCOMPATIBLE,
  JWT_CONFIG_MISMATCH
}
