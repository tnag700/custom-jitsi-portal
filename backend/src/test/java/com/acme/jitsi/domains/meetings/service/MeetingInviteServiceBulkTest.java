package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingInviteServiceBulkTest {

  @Mock
  private MeetingInviteRepository inviteRepository;

  @Mock
  private MeetingRepository meetingRepository;

  private MeetingInviteService service;

  @BeforeEach
  void setUp() {
    service = new MeetingInviteService(
        inviteRepository,
        meetingRepository);
  }

  @Test
  void countByMeetingReturnsRepositoryValue() {
    String meetingId = "meeting-1";
    when(inviteRepository.countByMeetingId(meetingId)).thenReturn(5L);
    assertThat(service.countByMeeting(meetingId)).isEqualTo(5L);
  }

  @Test
  void rollbackConsumeNoOpWhenTokenMissing() {
    when(inviteRepository.findByToken("missing")).thenReturn(Optional.empty());
    service.rollbackConsume("missing");
  }

  @Test
  void listByMeetingStillValidatesMeeting() {
    String meetingId = "meeting-1";
    when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Meeting.class)));
    when(inviteRepository.findByMeetingId(meetingId, 0, 20)).thenReturn(List.of());
    assertThat(service.listByMeeting(meetingId, 0, 20)).isEmpty();
  }
}
