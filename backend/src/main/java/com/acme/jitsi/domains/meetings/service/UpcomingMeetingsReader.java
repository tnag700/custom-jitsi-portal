package com.acme.jitsi.domains.meetings.service;

import java.util.List;

public interface UpcomingMeetingsReader {

  List<UpcomingMeetingCard> listForSubject(String subject);
}
