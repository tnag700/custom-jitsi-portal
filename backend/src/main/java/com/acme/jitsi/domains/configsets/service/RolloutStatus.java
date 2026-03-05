package com.acme.jitsi.domains.configsets.service;

public enum RolloutStatus {
  PENDING,
  VALIDATING,
  APPLYING,
  SUCCEEDED,
  FAILED,
  ROLLED_BACK
}