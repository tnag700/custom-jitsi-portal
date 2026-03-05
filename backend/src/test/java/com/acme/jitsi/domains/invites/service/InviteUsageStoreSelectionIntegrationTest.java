package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.store.StoreSelectionStrategyFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class InviteUsageStoreSelectionIntegrationTest {

  @Test
  void consumesInviteInInMemoryMode() {
    InviteValidationService service = createService("in-memory", null);

    InviteValidationService.InviteResolution resolution = service.validateAndConsume("invite-it");

    assertThat(resolution.meetingId()).isEqualTo("meeting-a");
  }

  @Test
  void consumesInviteInRedisModeWhenRedisAvailable() {
    @SuppressWarnings("unchecked")
    ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    when(provider.getIfAvailable()).thenReturn(redisTemplate);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.increment("invites:usage:invite-it")).thenReturn(1L);

    InviteValidationService service = createService("redis", provider);

    InviteValidationService.InviteResolution resolution = service.validateAndConsume("invite-it");

    assertThat(resolution.meetingId()).isEqualTo("meeting-a");
  }

  private InviteValidationService createService(
      String atomicStore,
      ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
    InviteExchangeProperties properties = new InviteExchangeProperties();
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken("invite-it");
    invite.setMeetingId("meeting-a");
    invite.setExpiresAt(Instant.now().plusSeconds(3600));
    invite.setUsageLimit(1);

    properties.setAtomicStore(atomicStore);
    properties.setInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));

    ObjectProvider<StringRedisTemplate> provider = redisTemplateProvider;
    if (provider == null) {
      @SuppressWarnings("unchecked")
      ObjectProvider<StringRedisTemplate> nullProvider = mock(ObjectProvider.class);
      when(nullProvider.getIfAvailable()).thenReturn(null);
      provider = nullProvider;
    }

    InMemoryInviteUsageStore inMemoryStore = new InMemoryInviteUsageStore();
    RedisInviteUsageStore redisStore = new RedisInviteUsageStore(provider);
    InviteUsageStoreResolver resolver =
        new InviteUsageStoreResolver(properties, inMemoryStore, redisStore, new StoreSelectionStrategyFactory());
    InviteUsageStoreRouter storeRouter = new InviteUsageStoreRouter(resolver);

    return new InviteValidationService(properties, storeRouter, createValidationChain());
  }

  private InviteValidationChain createValidationChain() {
    return new InviteValidationChain(List.of(
        new InviteTokenBlankValidator(),
        new InviteTokenExistsValidator(),
        new InviteRevokedValidator(),
        new InviteExpirationValidator(),
        new InviteMeetingKnownValidator(),
        new InviteMeetingStateValidator(mock(MeetingStateGuard.class)),
        new InviteUsageLimitValidator()));
  }
}