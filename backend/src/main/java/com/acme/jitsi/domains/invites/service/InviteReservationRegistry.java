package com.acme.jitsi.domains.invites.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class InviteReservationRegistry {

  private final Map<String, InviteReservation> activeReservations = new ConcurrentHashMap<>();

  InviteReservation issue(String inviteToken, String meetingId) {
    String reservationId = UUID.randomUUID().toString();
    InviteReservation reservation = InviteReservation.issue(reservationId, inviteToken, meetingId);
    activeReservations.put(reservationId, reservation);
    return reservation;
  }

  boolean authorizeRollback(InviteReservation reservation) {
    if (reservation == null) {
      return false;
    }
    return activeReservations.remove(reservation.reservationId(), reservation);
  }
}