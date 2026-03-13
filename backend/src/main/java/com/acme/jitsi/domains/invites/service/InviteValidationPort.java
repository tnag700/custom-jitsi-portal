package com.acme.jitsi.domains.invites.service;

/**
 * Application-facing invite boundary.
 * Shared responsibilities across runtime modes are explicit: validate, reserve/consume, rollback.
 * Mode-specific consume mechanics remain an implementation detail behind reserve.
 */
public interface InviteValidationPort extends InviteValidationCapability, InviteReservationCapability {
}