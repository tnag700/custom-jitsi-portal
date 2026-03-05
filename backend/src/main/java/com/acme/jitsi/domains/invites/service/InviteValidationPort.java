package com.acme.jitsi.domains.invites.service;

interface InviteValidationPort {

  InviteValidationService.InviteResolution validate(String inviteToken);

  InviteValidationService.InviteResolution validateAndConsume(String inviteToken);

  InviteValidationService.InviteReservation reserve(String inviteToken);

  void rollback(InviteValidationService.InviteReservation reservation);
}