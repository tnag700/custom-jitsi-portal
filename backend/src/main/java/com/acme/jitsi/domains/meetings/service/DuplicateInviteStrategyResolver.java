package com.acme.jitsi.domains.meetings.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DuplicateInviteStrategyResolver {

  private final Map<DuplicateHandlingPolicy, DuplicateInviteHandlingStrategy> strategies;

  public DuplicateInviteStrategyResolver(List<DuplicateInviteHandlingStrategy> strategyList) {
    Map<DuplicateHandlingPolicy, DuplicateInviteHandlingStrategy> mapping =
        new EnumMap<>(DuplicateHandlingPolicy.class);
    for (DuplicateInviteHandlingStrategy strategy : strategyList) {
      mapping.put(strategy.policy(), strategy);
    }
    this.strategies = Map.copyOf(mapping);
  }

  public DuplicateInviteHandlingStrategy resolve(DuplicateHandlingPolicy policy) {
    DuplicateInviteHandlingStrategy strategy = strategies.get(policy);
    if (strategy == null) {
      throw new IllegalArgumentException("Unsupported duplicate policy: " + policy);
    }
    return strategy;
  }
}
