package com.acme.jitsi.domains.admin.infrastructure;

import com.acme.jitsi.domains.admin.service.AdminIncidentCoordinationPort;
import com.acme.jitsi.domains.admin.service.AdminIncidentsInvalidRequestException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.features.admin-incident-coordination", havingValue = "false", matchIfMissing = true)
class AdminIncidentCoordinationDisabledAdapter implements AdminIncidentCoordinationPort {

  @Override
  public CoordinationSnapshot describe(CoordinationContext context) {
    return new CoordinationSnapshot(
        false,
        "disabled",
        "Coordination seam не активирован. Epic 21 остаётся завершабельным без ownership/status workflow.",
        null,
        "not-enabled",
        null,
        "not-linked",
        null,
        List.of());
  }

  @Override
  public CoordinationSnapshot update(CoordinationUpdateCommand command) {
    throw new AdminIncidentsInvalidRequestException(
        "Coordination seam не активирован для текущего окружения.");
  }

  @Override
  public CoordinationSnapshot recordTicketLink(TicketLinkCommand command) {
    return describe(command.context());
  }
}