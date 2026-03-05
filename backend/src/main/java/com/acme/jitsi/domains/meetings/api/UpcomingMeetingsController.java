package com.acme.jitsi.domains.meetings.api;

import com.acme.jitsi.domains.meetings.service.UpcomingMeetingCard;
import com.acme.jitsi.domains.meetings.service.UpcomingMeetingsReader;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/meetings", version = "v1")
class UpcomingMeetingsController {

  private final UpcomingMeetingsReader upcomingMeetingsReader;

  UpcomingMeetingsController(UpcomingMeetingsReader upcomingMeetingsReader) {
    this.upcomingMeetingsReader = upcomingMeetingsReader;
  }

  @GetMapping("/upcoming")
  List<UpcomingMeetingCardResponse> listUpcoming(@AuthenticationPrincipal OAuth2User principal) {
    return upcomingMeetingsReader.listForSubject(principal.getName()).stream()
        .map(UpcomingMeetingsController::toResponse)
        .toList();
  }

  private static UpcomingMeetingCardResponse toResponse(UpcomingMeetingCard card) {
    return new UpcomingMeetingCardResponse(
        card.meetingId(),
        card.title(),
        card.startsAt(),
        card.roomName(),
        card.joinAvailability());
  }
}
