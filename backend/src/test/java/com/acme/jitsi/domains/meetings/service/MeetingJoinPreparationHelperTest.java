package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MeetingJoinPreparationHelperTest {

  @Test
  void roomKeyIsUniqueAcrossSameTitlesAndStableAfterRename() {
    MeetingService meetings = Mockito.mock(MeetingService.class);
    MeetingTokenProperties properties = new MeetingTokenProperties();
    properties.setJoinUrlTemplate("https://meet.example/%s#jwt=%s");
    MeetingJoinPreparationHelper helper = new MeetingJoinPreparationHelper(
        meetings, Mockito.mock(MeetingProfilesPort.class), properties);

    String firstId = "11111111-1111-4111-8111-111111111111";
    String secondId = "22222222-2222-4222-8222-222222222222";
    when(meetings.getMeeting(firstId))
        .thenReturn(Meeting.builder().meetingId(firstId).title("Общее название").build())
        .thenReturn(Meeting.builder().meetingId(firstId).title("Новое название").build());
    when(meetings.getMeeting(secondId))
        .thenReturn(Meeting.builder().meetingId(secondId).title("Общее название").build());

    assertThat(helper.buildJoinUrl(firstId, "token", null))
        .startsWith("https://meet.example/" + firstId + "#jwt=");
    assertThat(helper.buildJoinUrl(secondId, "token", null))
        .startsWith("https://meet.example/" + secondId + "#jwt=");
    assertThat(helper.resolveRoomClaim(firstId)).isEqualTo(firstId);
    assertThat(helper.resolveRoomClaim(secondId)).isEqualTo(secondId);
  }
}
