package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentTicketPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.features.admin-incident-ticketing", havingValue = "true")
class AdminIncidentTicketTemplateAdapter implements AdminIncidentTicketPort {

  private final String ticketBaseUrl;

  AdminIncidentTicketTemplateAdapter(@Value("${app.admin.incidents.ticket-base-url:}") String ticketBaseUrl) {
    this.ticketBaseUrl = ticketBaseUrl;
  }

  @Override
  public TicketingStatus describeTicketing(TicketContext context) {
    return new TicketingStatus(hasBaseUrl(), null, null, hasBaseUrl() ? "available" : "misconfigured");
  }

  @Override
  public TicketCreationResult createTicket(TicketContext context) {
    if (!hasBaseUrl()) {
      return new TicketCreationResult(false, false, null, null, context.summary(), "Ticket base URL is not configured.");
    }
    String ticketKey = "INC-" + shortHash(context.incidentId());
    return new TicketCreationResult(
        true,
        true,
        ticketKey,
        ticketBaseUrl.endsWith("/") ? ticketBaseUrl + ticketKey : ticketBaseUrl + "/" + ticketKey,
        context.summary(),
        null);
  }

  private boolean hasBaseUrl() {
    return ticketBaseUrl != null && !ticketBaseUrl.isBlank();
  }

  private String shortHash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, 3).toUpperCase();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}