package com.acme.jitsi.infrastructure.audit;

import java.time.Duration;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditPublicationMaintenance {
    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;

    public AuditPublicationMaintenance(IncompleteEventPublications incomplete, CompletedEventPublications completed) {
        this.incomplete = incomplete;
        this.completed = completed;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void recover() {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(5));
    }

    @Scheduled(fixedDelayString = "PT24H", initialDelayString = "PT1H")
    public void pruneCompleted() {
        // Audit records themselves are retained; only successfully delivered publications are pruned.
        completed.deletePublicationsOlderThan(Duration.ofDays(30));
    }
}
