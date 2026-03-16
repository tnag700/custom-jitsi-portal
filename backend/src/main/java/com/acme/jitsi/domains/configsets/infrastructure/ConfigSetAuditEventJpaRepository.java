package com.acme.jitsi.domains.configsets.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConfigSetAuditEventJpaRepository extends JpaRepository<ConfigSetAuditEventEntity, String> {

	long countByConfigSetIdAndEventType(String configSetId, String eventType);

	boolean existsByConfigSetIdAndEventTypeAndTraceId(String configSetId, String eventType, String traceId);
}