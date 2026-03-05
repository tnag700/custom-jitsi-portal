package com.acme.jitsi.domains.invites.service;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class DatabaseInviteModeCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Environment env = context.getEnvironment();
    String mode = env.getProperty("app.invites.mode", "properties");
    return "database".equalsIgnoreCase(mode);
  }
}
