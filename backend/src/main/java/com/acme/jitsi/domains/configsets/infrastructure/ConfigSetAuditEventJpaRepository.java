package com.acme.jitsi.domains.configsets.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface ConfigSetAuditEventJpaRepository extends JpaRepository<ConfigSetAuditEventEntity, String> {
}