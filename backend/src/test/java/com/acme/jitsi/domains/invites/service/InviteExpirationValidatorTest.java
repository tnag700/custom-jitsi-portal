package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteExpirationValidatorTest {

  private final InviteExpirationValidator validator = new InviteExpirationValidator();

  @Test
  void rejectsExpiredInvite() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-expired",
        "meeting-a",
        Instant.now().minusSeconds(60),
        false,
        1);
    InviteValidationContext context = InviteValidationTestFixtures.context(
        invite.token(),
        InviteValidationTestFixtures.propertiesWithInvites(List.of(invite)));
    context.setInvite(invite);

    assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException ex = (MeetingTokenException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.GONE);
          assertThat(ex.errorCode()).isEqualTo("INVITE_EXPIRED");
        });
  }

  @Test
  void passesWhenInviteNotExpired() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-valid",
        "meeting-a",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteValidationContext context = InviteValidationTestFixtures.context(
        invite.token(),
        InviteValidationTestFixtures.propertiesWithInvites(List.of(invite)));
    context.setInvite(invite);

    validator.validate(context);
  }
}