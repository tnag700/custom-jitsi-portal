package com.acme.jitsi.domains.invites.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteUsageLimitValidatorTest {

  private final InviteUsageLimitValidator validator = new InviteUsageLimitValidator();

  @Test
  void delegatesAssertCanConsumeToUsageStoreRouter() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-usage",
        "meeting-a",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteUsageStoreRouter router = mock(InviteUsageStoreRouter.class);
    InviteValidationContext context = new InviteValidationContext(invite.token(), properties, router);
    context.setInvite(invite);

    validator.validate(context);

    verify(router).assertCanConsume(invite);
  }

  @Test
  void propagatesUsageLimitException() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-exhausted",
        "meeting-a",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteUsageStoreRouter router = mock(InviteUsageStoreRouter.class);
    InviteValidationContext context = new InviteValidationContext(invite.token(), properties, router);
    context.setInvite(invite);

    doThrow(new MeetingTokenException(HttpStatus.CONFLICT, "INVITE_EXHAUSTED", "Лимит использований инвайта исчерпан."))
      .when(router)
      .assertCanConsume(invite);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(MeetingTokenException.class)
        .extracting(error -> ((MeetingTokenException) error).errorCode())
        .isEqualTo("INVITE_EXHAUSTED");
  }
}