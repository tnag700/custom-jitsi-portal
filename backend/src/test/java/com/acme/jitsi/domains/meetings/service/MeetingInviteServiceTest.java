package com.acme.jitsi.domains.meetings.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingInviteServiceTest {

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
  void listByMeetingReturnsInvitesWhenMeetingExists() {
    String meetingId = "meeting-1";
    when(meetingRepository.findById(meetingId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Meeting.class)));
    when(inviteRepository.findByMeetingId(meetingId, 0, 20)).thenReturn(List.of());

    assertThat(service.listByMeeting(meetingId, 0, 20)).isEmpty();
  }

  @Test
  void listByMeetingThrowsWhenMeetingMissing() {
    when(meetingRepository.findById("missing")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.listByMeeting("missing", 0, 20))
        .isInstanceOf(MeetingNotFoundException.class);
  }

  @Test
  void findByTokenReturnsEmptyOptionalWhenMissing() {
    when(inviteRepository.findByToken("unknown")).thenReturn(Optional.empty());
    assertThat(service.findByToken("unknown")).isEmpty();
  }

  @Test
  void rollbackConsumeDecrementsCounter() {
    MeetingInvite invite = new MeetingInvite(
        "invite-1",
        "meeting-1",
        "token",
        MeetingRole.PARTICIPANT,
        2,
        1,
        Instant.now().plusSeconds(3600),
        null,
        Instant.now(),
        "creator");
    when(inviteRepository.findByToken("token")).thenReturn(Optional.of(invite));
    when(inviteRepository.save(org.mockito.ArgumentMatchers.argThat(saved -> saved.usedCount() == 0)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.rollbackConsume("token");
  }

  @Test
  void withUsedCount_preservesVersionForOCC() {
    // version field must be carried through withUsedCount so OCC works in JpaMeetingInviteRepository
    MeetingInvite invite = new MeetingInvite(
        "invite-1", "meeting-1", "token", MeetingRole.PARTICIPANT,
        2, 0, Instant.now().plusSeconds(3600), null, Instant.now(), "creator", null, null, 42L);

    MeetingInvite updated = invite.withUsedCount(1);

    assertThat(updated.version()).isEqualTo(42L);
    assertThat(updated.usedCount()).isEqualTo(1);
  }
}
