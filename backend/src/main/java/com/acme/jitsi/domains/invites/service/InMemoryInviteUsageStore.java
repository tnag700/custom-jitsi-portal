package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class InMemoryInviteUsageStore implements InviteUsageStore {

  private final Map<String, AtomicInteger> usageCounters = new ConcurrentHashMap<>();

  @Override
  public void assertCanConsume(InviteExchangeProperties.Invite invite) {
    AtomicInteger counter = usageCounters.get(invite.token());
    int runtimeCount = counter == null ? 0 : counter.get();
    int current = invite.usedCount() + runtimeCount;
    if (current >= invite.usageLimit()) {
      throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.INVITE_EXHAUSTED.code(), "Лимит использований инвайта исчерпан.");
    }
  }

  @Override
  public void consume(InviteExchangeProperties.Invite invite) {
    AtomicInteger counter = usageCounters.computeIfAbsent(invite.token(), key -> new AtomicInteger(0));
    while (true) {
      int runtimeCount = counter.get();
      int current = invite.usedCount() + runtimeCount;
      if (current >= invite.usageLimit()) {
        throw new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.INVITE_EXHAUSTED.code(), "Лимит использований инвайта исчерпан.");
      }
      if (counter.compareAndSet(runtimeCount, runtimeCount + 1)) {
        return;
      }
    }
  }

  @Override
  public void rollback(String inviteToken) {
    AtomicInteger counter = usageCounters.get(inviteToken);
    if (counter == null) {
      return;
    }

    while (true) {
      int current = counter.get();
      if (current <= 0) {
        return;
      }
      if (counter.compareAndSet(current, current - 1)) {
        return;
      }
    }
  }
}