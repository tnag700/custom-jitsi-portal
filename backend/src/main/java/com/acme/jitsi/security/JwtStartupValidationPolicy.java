package com.acme.jitsi.security;

interface JwtStartupValidationPolicy {
  void validateOrThrow();
}