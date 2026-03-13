package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.shared.ErrorCode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteMeetingStateValidatorTest {

    private final InviteMeetingStatePort inviteMeetingStatePort = mock(InviteMeetingStatePort.class);

  private final InviteMeetingStateValidator validator =
      new InviteMeetingStateValidator(inviteMeetingStatePort);

  @Test
  void rejectsClosedMeeting() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-closed",
        "meeting-closed",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteValidationContext context = InviteValidationTestFixtures.context(invite.token(), properties);
    context.setInvite(invite);

    doThrow(new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена."))
      .when(inviteMeetingStatePort)
      .assertJoinAllowed("meeting-closed");

    assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException ex = (InviteExchangeException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.errorCode()).isEqualTo(ErrorCode.MEETING_ENDED.code());
        });

    verify(inviteMeetingStatePort).assertJoinAllowed("meeting-closed");
  }

  @Test
  void rejectsCanceledMeeting() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-canceled",
        "meeting-canceled",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteValidationContext context = InviteValidationTestFixtures.context(invite.token(), properties);
    context.setInvite(invite);

    doThrow(new InviteExchangeException(HttpStatus.CONFLICT, ErrorCode.MEETING_CANCELED.code(), "Встреча отменена."))
      .when(inviteMeetingStatePort)
      .assertJoinAllowed("meeting-canceled");

    assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException ex = (InviteExchangeException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.errorCode()).isEqualTo(ErrorCode.MEETING_CANCELED.code());
        });

    verify(inviteMeetingStatePort).assertJoinAllowed("meeting-canceled");
  }

  @Test
  void passesWhenMeetingIsOpen() {
    InviteExchangeProperties.Invite invite = InviteValidationTestFixtures.invite(
        "invite-open",
        "meeting-open",
        Instant.now().plusSeconds(600),
        false,
        1);
    InviteExchangeProperties properties = InviteValidationTestFixtures.propertiesWithInvites(List.of(invite));
    InviteValidationContext context = InviteValidationTestFixtures.context(invite.token(), properties);
    context.setInvite(invite);

    doNothing().when(inviteMeetingStatePort).assertJoinAllowed("meeting-open");

    validator.validate(context);

    verify(inviteMeetingStatePort).assertJoinAllowed("meeting-open");
  }
}