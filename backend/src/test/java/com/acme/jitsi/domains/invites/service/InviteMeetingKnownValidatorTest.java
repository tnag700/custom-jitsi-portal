package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteMeetingKnownValidatorTest {

  private final InviteMeetingKnownValidator validator = new InviteMeetingKnownValidator();

  @Test
  void rejectsUnknownMeetingWhenKnownSetDefined() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-valid",
        "meeting-unknown",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    InviteValidationContext context = InviteValidationTestFixtures.context(invite.token(), properties);
    context.setInvite(invite);

    assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException ex = (MeetingTokenException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.errorCode()).isEqualTo("MEETING_NOT_FOUND");
        });
  }

  @Test
  void passesWhenKnownMeetingsNotConfigured() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-valid",
        "meeting-any",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteValidationContext context = InviteValidationTestFixtures.context(invite.token(), properties);
    context.setInvite(invite);

    validator.validate(context);
  }
}