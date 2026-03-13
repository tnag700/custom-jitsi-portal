package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.jitsi.domains.meetings.service.Meeting;
import com.acme.jitsi.domains.meetings.service.MeetingStatus;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JpaMeetingRepositoryTest {

  @Mock
  private MeetingJpaRepository jpaRepository;

  private JpaMeetingRepository repository;

  @BeforeEach
  void setUp() {
    repository = new JpaMeetingRepository(jpaRepository);
  }

  @Test
  void savePersistsDetachedEntityWithoutPreRead() {
    Meeting meeting = new Meeting(
        "meeting-1",
        "room-1",
        "Planning",
        "Sprint planning",
        "scheduled",
        "config-1",
        MeetingStatus.SCHEDULED,
        Instant.parse("2026-03-11T10:00:00Z"),
        Instant.parse("2026-03-11T11:00:00Z"),
        true,
        false,
        Instant.parse("2026-03-11T09:00:00Z"),
        Instant.parse("2026-03-11T09:30:00Z"));
    when(jpaRepository.save(any(MeetingEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    Meeting saved = repository.save(meeting);

    assertThat(saved).isEqualTo(meeting);
    verify(jpaRepository).save(any(MeetingEntity.class));
    verify(jpaRepository, never()).findById(anyString());
  }
}