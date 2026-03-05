package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteTokenBlankValidatorTest {

  private final InviteTokenBlankValidator validator = new InviteTokenBlankValidator();

  @Test
  void rejectsBlankToken() {
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of());

    assertThatThrownBy(() -> validator.validate(InviteValidationTestFixtures.context(" ", properties)))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException ex = (MeetingTokenException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.errorCode()).isEqualTo("INVALID_INVITE");
        });
  }

  @Test
  void passesWhenTokenIsNotBlank() {
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of());

    validator.validate(InviteValidationTestFixtures.context("invite-ok", properties));
  }
}