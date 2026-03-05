package com.acme.jitsi.security;

interface JwtStartupValidationReporter {
  void report(JwtStartupValidationEvent event);
}