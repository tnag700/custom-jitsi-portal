package com.acme.jitsi.domains.invites.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InviteReservationRegistry {

  private final Map<String, InviteReservation> activeReservations = new ConcurrentHashMap<>();

  public InviteReservation issue(String inviteToken, String meetingId) {
    String reservationId = UUID.randomUUID().toString();
    InviteReservation reservation = InviteReservation.issue(reservationId, inviteToken, meetingId);
    activeReservations.put(reservationId, reservation);
    return reservation;
  }

  public boolean authorizeRollback(InviteReservation reservation) {
    return remove(reservation);
  }

  public void complete(InviteReservation reservation) {
    remove(reservation);
  }

  private boolean remove(InviteReservation reservation) {
    if (reservation == null) {
      return false;
    }
    return activeReservations.remove(reservation.reservationId(), reservation);
  }
}
