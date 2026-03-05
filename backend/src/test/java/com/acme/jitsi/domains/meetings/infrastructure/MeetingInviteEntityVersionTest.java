package com.acme.jitsi.domains.meetings.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.jitsi.domains.meetings.service.MeetingInvite;
import com.acme.jitsi.domains.meetings.service.MeetingRole;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Verifies that MeetingInviteEntity correctly propagates the @Version field through toDomain()
 * and updateFrom(). This is essential for OCC (Optimistic Concurrency Control) to work.
 */
class MeetingInviteEntityVersionTest {

  @Test
  void toDomain_propagatesVersionFromEntity() {
    MeetingInviteEntity entity = buildEntity();
    // Simulate JPA setting version to 3 after several saves
    setVersion(entity, 3L);

    MeetingInvite domain = entity.toDomain();

    assertThat(domain.version()).isEqualTo(3L);
  }

  @Test
  void updateFrom_overridesEntityVersionWithDomainVersion() {
    MeetingInviteEntity entity = buildEntity();
    setVersion(entity, 5L);

    // Domain record carries stale version = 2 (simulating state before concurrent update)
    MeetingInvite staleInvite = new MeetingInvite(
        "invite-1", "meeting-1", "token-abc", MeetingRole.PARTICIPANT,
        1, 0, Instant.now().plusSeconds(3600), null, Instant.now(), "creator",
        null, null, 2L);

    entity.updateFrom(staleInvite);

    // After updateFrom, entity must carry the domain's version (stale = 2),
    // so JPA UPDATE WHERE version = 2 fails if DB is at version 5 → OCC triggers
    assertThat(entity.toDomain().version()).isEqualTo(2L);
  }

  @Test
  void toDomain_roundTrip_preservesAllFields() {
    MeetingInviteEntity entity = buildEntity();
    setVersion(entity, 7L);

    MeetingInvite domain = entity.toDomain();
    // Simulate consuming invite — version must stay intact through withUsedCount
    MeetingInvite updated = domain.withUsedCount(1);

    assertThat(updated.version()).isEqualTo(7L);
    assertThat(updated.usedCount()).isEqualTo(1);
  }

  // --- helpers ---

  private MeetingInviteEntity buildEntity() {
    MeetingInvite invite = new MeetingInvite(
        "invite-1", "meeting-1", "token-abc", MeetingRole.PARTICIPANT,
        2, 0, Instant.now().plusSeconds(3600), null, Instant.now(), "creator",
        null, null, 0L);
    return new MeetingInviteEntity(invite);
  }

  /** Reflectively set the @Version field — needed because it is package-private. */
  private void setVersion(MeetingInviteEntity entity, long version) {
    try {
      var field = MeetingInviteEntity.class.getDeclaredField("version");
      field.setAccessible(true);
      field.set(entity, version);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError("Cannot set version field", e);
    }
  }
}
