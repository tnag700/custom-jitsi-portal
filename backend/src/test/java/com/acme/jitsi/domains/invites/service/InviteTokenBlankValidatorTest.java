package com.acme.jitsi.domains.invites.service;

import com.acme.jitsi.shared.ErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteTokenBlankValidatorTest {

  private final InviteTokenBlankValidator validator = new InviteTokenBlankValidator();

  @Test
  void rejectsBlankToken() {
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of());

    assertThatThrownBy(() -> validator.validate(InviteValidationTestFixtures.context(" ", properties)))
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException ex = (InviteExchangeException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.NOT_FOUND);
          assertThat(ex.errorCode()).isEqualTo(ErrorCode.INVITE_NOT_FOUND.code());
        });
  }

  @Test
  void passesWhenTokenIsNotBlank() {
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of());

    validator.validate(InviteValidationTestFixtures.context("invite-ok", properties));
  }
}

