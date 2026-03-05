package com.acme.jitsi.security;

enum JwtStartupValidationErrorCode {
  NONE,
  CONFIG_MISSING_REQUIRED,
  CONFIG_INCOMPATIBLE,
  JWT_CONFIG_MISMATCH
}