package com.acme.jitsi.domains.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuthAuditEventJpaRepository extends JpaRepository<AuthAuditEventEntity, Long> {
}