package com.acme.jitsi.domains.meetings.service;

import java.util.List;
import java.util.Optional;

public interface MeetingParticipantAssignmentRepository {

  Optional<MeetingParticipantAssignment> findByMeetingIdAndSubjectId(String meetingId, String subjectId);

  List<MeetingParticipantAssignment> findByMeetingId(String meetingId);

  List<MeetingParticipantAssignment> findBySubjectId(String subjectId);

  MeetingParticipantAssignment save(MeetingParticipantAssignment assignment);

  void delete(MeetingParticipantAssignment assignment);
}