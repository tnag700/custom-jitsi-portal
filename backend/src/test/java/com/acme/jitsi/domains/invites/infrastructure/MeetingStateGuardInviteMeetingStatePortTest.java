package com.acme.jitsi.domains.invites.infrastructure;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.acme.jitsi.domains.invites.service.InviteExchangeException;
import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import com.acme.jitsi.domains.meetings.service.MeetingTokenException;
import com.acme.jitsi.shared.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MeetingStateGuardInviteMeetingStatePortTest {

  private final MeetingStateGuard meetingStateGuard = mock(MeetingStateGuard.class);
  private final MeetingStateGuardInviteMeetingStatePort port =
      new MeetingStateGuardInviteMeetingStatePort(meetingStateGuard);

  @Test
  void assertJoinAllowedDelegatesWhenMeetingIsAllowed() {
    doNothing().when(meetingStateGuard).assertJoinAllowed("meeting-open");

    port.assertJoinAllowed("meeting-open");

    verify(meetingStateGuard).assertJoinAllowed("meeting-open");
  }

  @Test
  void assertJoinAllowedMapsMeetingTokenExceptionToInviteExchangeException() {
    doThrow(new MeetingTokenException(HttpStatus.CONFLICT, ErrorCode.MEETING_ENDED.code(), "Встреча завершена."))
        .when(meetingStateGuard)
        .assertJoinAllowed("meeting-ended");

    assertThatThrownBy(() -> port.assertJoinAllowed("meeting-ended"))
        .isInstanceOf(InviteExchangeException.class)
        .satisfies(error -> {
          InviteExchangeException exception = (InviteExchangeException) error;
          org.assertj.core.api.Assertions.assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
          org.assertj.core.api.Assertions.assertThat(exception.errorCode()).isEqualTo(ErrorCode.MEETING_ENDED.code());
          org.assertj.core.api.Assertions.assertThat(exception.getCause()).isInstanceOf(MeetingTokenException.class);
        });
  }
}