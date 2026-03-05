package com.acme.jitsi.domains.meetings.service;

public class MeetingAssignmentNotFoundException extends RuntimeException {
  public MeetingAssignmentNotFoundException(String meetingId, String subjectId) {
    super("Назначение участника '" + subjectId + "' на встречу '" + meetingId + "' не найдено");
  }
}
