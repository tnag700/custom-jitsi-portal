package com.acme.jitsi.domains.invites.api;

import com.acme.jitsi.domains.invites.service.InviteExchangeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/invites", version = "v1")
class InviteExchangeController {

  private final InviteExchangeService inviteExchangeService;

  InviteExchangeController(InviteExchangeService inviteExchangeService) {
    this.inviteExchangeService = inviteExchangeService;
  }

  @GetMapping("/{inviteToken}/validate")
  InviteValidationResponse validate(@PathVariable String inviteToken) {
    InviteExchangeService.ValidationResult result = inviteExchangeService.validate(inviteToken);
    return new InviteValidationResponse(true, result.meetingId());
  }

  @PostMapping("/exchange")
  InviteExchangeResponse exchange(@Valid @RequestBody InviteExchangeRequest request) {
    InviteExchangeService.ExchangeResult result =
        inviteExchangeService.exchange(request.inviteToken(), request.displayName());
    return new InviteExchangeResponse(result.joinUrl(), result.expiresAt(), result.role(), result.meetingId());
  }
}