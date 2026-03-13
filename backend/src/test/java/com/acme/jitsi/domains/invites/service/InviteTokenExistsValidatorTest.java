package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteTokenExistsValidatorTest {

  private final InviteTokenExistsValidator validator = new InviteTokenExistsValidator();

  @Test
  void resolvesInviteByToken() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-a",
        "meeting-a",
        Instant.now().plusSeconds(600),
        false,
        2);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteValidationContext context = InviteValidationTestFixtures.context("invite-a", properties);

    validator.validate(context);

    assertThat(context.invite()).isSameAs(invite);
  }

  @Test
  void rejectsUnknownToken() {
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of());

    assertThatThrownBy(() -> validator.validate(InviteValidationTestFixtures.context("missing", properties)))
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException ex = (InviteExchangeException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVITE_NOT_FOUND.code());
        });
  }
}

