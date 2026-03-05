package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
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
    int current = counter == null ? 0 : counter.get();
    if (current >= invite.usageLimit()) {
      throw new MeetingTokenException(HttpStatus.CONFLICT, "INVITE_EXHAUSTED", "Лимит использований инвайта исчерпан.");
    }
  }

  @Override
  public void consume(InviteExchangeProperties.Invite invite) {
    AtomicInteger counter = usageCounters.computeIfAbsent(invite.token(), key -> new AtomicInteger(0));
    while (true) {
      int current = counter.get();
      if (current >= invite.usageLimit()) {
        throw new MeetingTokenException(HttpStatus.CONFLICT, "INVITE_EXHAUSTED", "Лимит использований инвайта исчерпан.");
      }
      if (counter.compareAndSet(current, current + 1)) {
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