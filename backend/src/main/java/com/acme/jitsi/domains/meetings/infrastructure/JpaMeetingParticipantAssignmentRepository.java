package com.acme.jitsi.domains.meetings.infrastructure;

import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignment;
import com.acme.jitsi.domains.meetings.service.MeetingParticipantAssignmentRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaMeetingParticipantAssignmentRepository
    extends JpaRepository<MeetingParticipantAssignmentEntity, String>, MeetingParticipantAssignmentRepository {

  @Query("SELECT a FROM MeetingParticipantAssignmentEntity a WHERE a.meetingId = :meetingId AND a.subjectId = :subjectId")
  Optional<MeetingParticipantAssignmentEntity> findEntityByMeetingIdAndSubjectId(
      @Param("meetingId") String meetingId,
      @Param("subjectId") String subjectId);

  @Query("SELECT a FROM MeetingParticipantAssignmentEntity a WHERE a.meetingId = :meetingId ORDER BY a.assignedAt")
  List<MeetingParticipantAssignmentEntity> findEntitiesByMeetingId(@Param("meetingId") String meetingId);

  @Query("SELECT a FROM MeetingParticipantAssignmentEntity a WHERE a.subjectId = :subjectId")
  List<MeetingParticipantAssignmentEntity> findEntitiesBySubjectId(@Param("subjectId") String subjectId);

  boolean existsByMeetingIdAndSubjectId(String meetingId, String subjectId);

  void deleteByMeetingIdAndSubjectId(String meetingId, String subjectId);

  // Implementation of interface methods returning domain types
  @Override
  default MeetingParticipantAssignment save(MeetingParticipantAssignment assignment) {
    return findEntityByMeetingIdAndSubjectId(assignment.meetingId(), assignment.subjectId())
        .map(entity -> {
          entity.updateFrom(assignment);
          return saveAndFlush(entity).toDomain();
        })
        .orElseGet(() -> saveAndFlush(new MeetingParticipantAssignmentEntity(assignment)).toDomain());
  }

  @Override
  default void delete(MeetingParticipantAssignment assignment) {
    deleteByMeetingIdAndSubjectId(assignment.meetingId(), assignment.subjectId());
  }

  @Override
  default Optional<MeetingParticipantAssignment> findByMeetingIdAndSubjectId(String meetingId, String subjectId) {
    return findEntityByMeetingIdAndSubjectId(meetingId, subjectId).map(MeetingParticipantAssignmentEntity::toDomain);
  }

  @Override
  default List<MeetingParticipantAssignment> findByMeetingId(String meetingId) {
    return findEntitiesByMeetingId(meetingId).stream()
        .map(MeetingParticipantAssignmentEntity::toDomain)
        .toList();
  }

  @Override
  default List<MeetingParticipantAssignment> findBySubjectId(String subjectId) {
    return findEntitiesBySubjectId(subjectId).stream()
        .map(MeetingParticipantAssignmentEntity::toDomain)
        .toList();
  }
}
