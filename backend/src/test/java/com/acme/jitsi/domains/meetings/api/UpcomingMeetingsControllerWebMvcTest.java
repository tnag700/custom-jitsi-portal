package com.acme.jitsi.domains.meetings.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.jitsi.domains.meetings.service.JoinAvailability;
import com.acme.jitsi.domains.meetings.service.UpcomingMeetingCard;
import com.acme.jitsi.domains.meetings.service.UpcomingMeetingsReader;
import com.acme.jitsi.security.ProblemDetailsMappingPolicy;
import com.acme.jitsi.security.ProblemResponseFacade;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UpcomingMeetingsController.class)
@AutoConfigureMockMvc
@Tag("slice")
class UpcomingMeetingsControllerWebMvcTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UpcomingMeetingsReader upcomingMeetingsReader;

  @MockitoBean
  private ProblemResponseFacade problemResponseFacade;

  @MockitoBean
  private ProblemDetailsMappingPolicy problemDetailsMappingPolicy;

  @Test
  void authenticatedRequestReturnsUpcomingMeetingCardsSortedAscending() throws Exception {
    when(upcomingMeetingsReader.listForSubject(anyString())).thenReturn(List.of(
        new UpcomingMeetingCard(
            "meeting-a",
            "Architecture sync",
            Instant.parse("2099-01-01T08:00:00Z"),
            "Room-A",
            JoinAvailability.SCHEDULED),
        new UpcomingMeetingCard(
            "meeting-b",
            "Daily standup",
            Instant.parse("2099-01-01T09:00:00Z"),
            "Room-B",
            JoinAvailability.SCHEDULED)));

    mockMvc.perform(get("/api/v1/meetings/upcoming")
            .with(oauth2Login().attributes(attrs -> attrs.put("sub", "u-host"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].meetingId").value("meeting-a"))
        .andExpect(jsonPath("$[0].title").value("Architecture sync"))
        .andExpect(jsonPath("$[0].startsAt").value("2099-01-01T08:00:00Z"))
        .andExpect(jsonPath("$[0].roomName").value("Room-A"))
        .andExpect(jsonPath("$[0].joinAvailability").value("scheduled"))
        .andExpect(jsonPath("$[1].meetingId").value("meeting-b"))
        .andExpect(jsonPath("$[1].title").value("Daily standup"))
        .andExpect(jsonPath("$[1].startsAt").value("2099-01-01T09:00:00Z"))
        .andExpect(jsonPath("$[1].roomName").value("Room-B"))
        .andExpect(jsonPath("$[1].joinAvailability").value("scheduled"));
  }
}