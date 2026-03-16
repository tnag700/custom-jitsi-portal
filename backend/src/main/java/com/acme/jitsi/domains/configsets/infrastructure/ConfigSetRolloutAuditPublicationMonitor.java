package com.acme.jitsi.domains.configsets.infrastructure;

import com.acme.jitsi.domains.configsets.event.ConfigSetRolloutCompletedEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.stereotype.Component;

@Component
class ConfigSetRolloutAuditPublicationMonitor {

  private final EventPublicationRegistry eventPublicationRegistry;
  private final IncompleteEventPublications incompleteEventPublications;

  ConfigSetRolloutAuditPublicationMonitor(
      EventPublicationRegistry eventPublicationRegistry,
      IncompleteEventPublications incompleteEventPublications) {
    this.eventPublicationRegistry = eventPublicationRegistry;
    this.incompleteEventPublications = incompleteEventPublications;
  }

  List<DurablePublicationSnapshot> findIncompletePilotPublications() {
    return eventPublicationRegistry.findIncompletePublications().stream()
        .filter(this::isPilotPublication)
        .map(this::toSnapshot)
        .toList();
  }

  void resubmitIncompletePilotPublications() {
    incompleteEventPublications.resubmitIncompletePublications(this::isPilotPublicationEvent);
  }

  private boolean isPilotPublication(TargetEventPublication publication) {
    return ConfigSetAuditListener.DURABLE_ROLLOUT_COMPLETED_LISTENER_ID.equals(
            publication.getTargetIdentifier().getValue())
        && publication.getEvent() instanceof ConfigSetRolloutCompletedEvent;
  }

  private boolean isPilotPublicationEvent(EventPublication publication) {
    return publication.getEvent() instanceof ConfigSetRolloutCompletedEvent;
  }

  private DurablePublicationSnapshot toSnapshot(TargetEventPublication publication) {
    return new DurablePublicationSnapshot(
        publication.getIdentifier(),
        publication.getTargetIdentifier().getValue(),
        publication.getEvent().getClass().getName(),
        publication.getStatus(),
        publication.getCompletionAttempts(),
        publication.getPublicationDate(),
        publication.getLastResubmissionDate());
  }

  record DurablePublicationSnapshot(
      java.util.UUID identifier,
      String listenerId,
      String eventType,
      EventPublication.Status status,
      int completionAttempts,
      Instant publicationDate,
      Instant lastResubmissionDate) {
  }
}