package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentTicketPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.features.admin-incident-ticketing", havingValue = "false", matchIfMissing = true)
class AdminIncidentTicketDisabledAdapter implements AdminIncidentTicketPort {

  @Override
  public TicketingStatus describeTicketing(TicketContext context) {
    return new TicketingStatus(false, null, null, "disabled");
  }

  @Override
  public TicketCreationResult createTicket(TicketContext context) {
    return new TicketCreationResult(false, false, null, null, context.summary(), "External ticketing is disabled.");
  }
}