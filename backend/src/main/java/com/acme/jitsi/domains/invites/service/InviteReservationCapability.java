package com.acme.jitsi.domains.invites.service;

public interface InviteReservationCapability {

  InviteReservation reserve(String inviteToken);

  void rollback(InviteReservation reservation);
}