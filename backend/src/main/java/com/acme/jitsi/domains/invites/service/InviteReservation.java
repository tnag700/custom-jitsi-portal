package com.acme.jitsi.domains.invites.service;

import java.util.Objects;

public final class InviteReservation {

	private final String reservationId;
	private final String inviteToken;
	private final String meetingId;

	private InviteReservation(String reservationId, String inviteToken, String meetingId) {
		this.reservationId = Objects.requireNonNull(reservationId, "reservationId must not be null");
		this.inviteToken = Objects.requireNonNull(inviteToken, "inviteToken must not be null");
		this.meetingId = Objects.requireNonNull(meetingId, "meetingId must not be null");
	}

	public static InviteReservation issue(String reservationId, String inviteToken, String meetingId) {
		return new InviteReservation(reservationId, inviteToken, meetingId);
	}

	public String reservationId() {
		return reservationId;
	}

	public String inviteToken() {
		return inviteToken;
	}

	public String meetingId() {
		return meetingId;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof InviteReservation that)) {
			return false;
		}
		return reservationId.equals(that.reservationId)
				&& inviteToken.equals(that.inviteToken)
				&& meetingId.equals(that.meetingId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(reservationId, inviteToken, meetingId);
	}

	@Override
	public String toString() {
		return "InviteReservation[reservationId=" + reservationId + "]";
	}
}