package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class InviteMeetingStateValidatorTest {

  private final MeetingStateGuard meetingStateGuard = mock(MeetingStateGuard.class);

  private final InviteMeetingStateValidator validator =
      new InviteMeetingStateValidator(meetingStateGuard);

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

    doThrow(new MeetingTokenException(HttpStatus.CONFLICT, "MEETING_ENDED", "Встреча завершена."))
      .when(meetingStateGuard)
      .assertJoinAllowed("meeting-closed");

    assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException ex = (MeetingTokenException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.errorCode()).isEqualTo("MEETING_ENDED");
        });

    verify(meetingStateGuard).assertJoinAllowed("meeting-closed");
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

    doThrow(new MeetingTokenException(HttpStatus.CONFLICT, "MEETING_CANCELED", "Встреча отменена."))
      .when(meetingStateGuard)
      .assertJoinAllowed("meeting-canceled");

    assertThatThrownBy(() -> validator.validate(context))
        .isInstanceOf(MeetingTokenException.class)
        .satisfies(error -> {
          MeetingTokenException ex = (MeetingTokenException) error;
          assertThat(ex.status()).isEqualTo(HttpStatus.CONFLICT);
          assertThat(ex.errorCode()).isEqualTo("MEETING_CANCELED");
        });

    verify(meetingStateGuard).assertJoinAllowed("meeting-canceled");
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

    doNothing().when(meetingStateGuard).assertJoinAllowed("meeting-open");

    validator.validate(context);

    verify(meetingStateGuard).assertJoinAllowed("meeting-open");
  }
}