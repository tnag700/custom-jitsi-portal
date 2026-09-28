package com.acme.jitsi.domains.invites.service;

public interface InviteReservationCapability {

  InviteReservation reserve(String inviteToken);

  void complete(InviteReservation reservation);

  void rollback(InviteReservation reservation);
}
