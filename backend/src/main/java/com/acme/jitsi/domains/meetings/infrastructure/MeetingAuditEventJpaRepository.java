package com.acme.jitsi.domains.meetings.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface MeetingAuditEventJpaRepository extends JpaRepository<MeetingAuditEventEntity, Long> {
}
