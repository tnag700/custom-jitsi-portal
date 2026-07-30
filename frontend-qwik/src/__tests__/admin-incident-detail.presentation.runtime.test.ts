/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import type {
  AdminIncidentDetail,
  IncidentDetailDerivedState,
} from "~/lib/domains/admin";
import {
  findNode,
  findNodes,
  renderNode,
  textContent,
} from "./support/jsx-tree";

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    component$:
      <TProps extends object>(render: (props: TProps) => unknown) =>
      (props: TProps) =>
        actual.jsx(render as never, props as never),
    componentQrl: <T>(value: T): T => value,
    inlinedQrl: <T>(value: T): T => value,
    inlinedQrlDEV: <T>(value: T): T => value,
    qrl: <T>(value: T): T => value,
  };
});

vi.mock("@qwik.dev/router", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    Form: (props: Record<string, unknown>) =>
      actual.jsx("form", {
        ...props,
        action: undefined,
      }),
  };
});

vi.mock("~/lib/shared", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    ApiErrorAlert: (props: Record<string, unknown>) =>
      actual.jsx("div", {
        "data-testid": "api-error",
        children: props.message,
      }),
    RequestStatePanel: (props: Record<string, unknown>) =>
      actual.jsx("div", {
        "data-testid": "request-state",
        children: [props.title, props.detail],
      }),
  };
});

function createIncidentDetail(): AdminIncidentDetail {
  return {
    incidentId: "incident-1",
    tenantId: "tenant-1",
    environment: "dev",
    errorCode: "ROLE_MISMATCH",
    category: "SSO",
    severity: "warn",
    summary: "У пользователя не совпала роль.",
    startedAt: "2026-07-29T10:00:00Z",
    endedAt: "2026-07-29T10:05:00Z",
    affectedAttempts: [],
    summaryBar: {
      title: "Несоответствие роли",
      refusalReason: "ROLE_MISMATCH / SSO",
      affectedScope: "1 пользователь",
      operationalStatus: "active-investigation",
      timeWindow: "5 минут",
      environment: "dev",
    },
    timeline: [
      {
        occurredAt: "2026-07-29T10:00:00Z",
        title: "Проверка роли",
        summary: "Требуемая роль не найдена.",
        subjectDisplay: "user@example.test",
        role: "doctor",
        traceId: "trace-1",
        correlationId: "correlation-1",
        roomId: null,
        meetingId: null,
      },
    ],
    evidence: [
      {
        kind: "audit",
        title: "Событие авторизации",
        status: "found",
        summary: "Сигнал подтверждён аудитом.",
        detail: "Запись найдена в журнале.",
        traceId: "trace-1",
        correlationId: "correlation-1",
        traceUrl: null,
        emptyState: null,
      },
    ],
    relatedLinks: [
      {
        kind: "role-history",
        label: "История ролей",
        environment: "dev",
        subjectId: "subject-1",
        roomId: null,
        meetingId: null,
        traceId: "trace-1",
        externalUrl: null,
      },
    ],
    nextActions: [
      {
        kind: "investigate",
        label: "Проверить историю ролей",
        detail: "Сопоставить назначение роли с отказом.",
        target: "role-history",
        externalUrl: null,
      },
    ],
    coordination: {
      enabled: false,
      availability: "optional",
      explanation: "Координация для окружения не включена.",
      owner: null,
      workflowStatus: "not-enabled",
      ticketReference: null,
      ticketStatus: "not-linked",
      ticketUrl: null,
      history: [],
    },
    ticketing: {
      available: true,
      ticketKey: null,
      ticketUrl: null,
      status: "available",
    },
  };
}

function createDerivedState(
  incident: AdminIncidentDetail,
): IncidentDetailDerivedState {
  return {
    coordination: incident.coordination,
    ticketing: incident.ticketing,
    effectiveTicketReference: null,
    effectiveTicketUrl: null,
    effectiveTicketStatus: incident.ticketing.status,
  };
}

describe("admin incident detail presentation", () => {
  it("renders a compact Russian investigation workspace and keeps optional coordination collapsed", async () => {
    const { AdminIncidentDetailOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const incident = createIncidentDetail();
    const tree = await renderNode(
      AdminIncidentDetailOverview({
        currentUrl:
          "http://localhost:3000/admin/incidents/incident-1?environment=dev&returnTo=%2Fadmin%2Fincidents%3Fperiod%3D24h",
        incident,
        detailState: createDerivedState(incident),
        canManageTicket: true,
        ticketAction: {},
        coordinationAction: {},
        ticketError: null,
        coordinationError: null,
      }),
    );
    const content = textContent(tree);
    const details = findNodes(tree, (node) => node.type === "details");
    const coordination = details.find((node) =>
      textContent(node).includes("Координация"),
    );
    const returnLink = findNode(
      tree,
      (node) =>
        node.type === "a" && textContent(node) === "К очереди инцидентов",
    );

    expect(content).toContain("Расследование инцидента");
    expect(content).toContain("Сигналы и диагностика");
    expect(content).toContain("Связанный контекст");
    expect(content).toContain("Создать тикет");
    expect(content).not.toContain("Investigation workspace");
    expect(content).not.toContain("First scan");
    expect(content).not.toContain("Diagnostics evidence");
    expect(content).not.toContain("Coordination seam");
    expect(content).not.toContain("External Ticketing");
    expect(coordination?.props.open).toBeFalsy();
    expect(returnLink?.props.href).toBe("/admin/incidents?period=24h");
    expect(findNodes(tree, (node) => node.type === "form")).toHaveLength(1);
  });

  it("opens enabled coordination for admins and preserves editable values", async () => {
    const { AdminIncidentDetailOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const incident = createIncidentDetail();
    incident.coordination = {
      ...incident.coordination,
      enabled: true,
      availability: "available",
      owner: "lead.support",
      workflowStatus: "investigating",
      ticketReference: "INC-42",
      ticketStatus: "linked",
    };
    const tree = await renderNode(
      AdminIncidentDetailOverview({
        currentUrl:
          "http://localhost:3000/admin/incidents/incident-1?environment=dev",
        incident,
        detailState: createDerivedState(incident),
        canManageTicket: true,
        ticketAction: {},
        coordinationAction: {},
        ticketError: null,
        coordinationError: null,
      }),
    );
    const details = findNodes(tree, (node) => node.type === "details");
    const coordination = details.find((node) =>
      textContent(node).includes("Координация"),
    );
    const controls = findNodes(
      tree,
      (node) => node.type === "input" || node.type === "select",
    );

    expect(coordination?.props.open).toBe(true);
    expect(
      controls.find((node) => node.props.name === "owner")?.props.value,
    ).toBe("lead.support");
    expect(
      controls.find((node) => node.props.name === "ticketReference")?.props
        .value,
    ).toBe("INC-42");
    expect(findNodes(tree, (node) => node.type === "form")).toHaveLength(2);
  });

  it("keeps the investigation readable without mutation forms for readonly roles", async () => {
    const { AdminIncidentDetailOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const incident = createIncidentDetail();
    incident.coordination = {
      ...incident.coordination,
      enabled: true,
      availability: "available",
      workflowStatus: "triage",
    };
    const tree = await renderNode(
      AdminIncidentDetailOverview({
        currentUrl:
          "http://localhost:3000/admin/incidents/incident-1?environment=dev",
        incident,
        detailState: createDerivedState(incident),
        canManageTicket: false,
        ticketAction: {},
        coordinationAction: {},
        ticketError: null,
        coordinationError: null,
      }),
    );
    const content = textContent(tree);

    expect(content).toContain("Сигналы и диагностика");
    expect(content).toContain("доступно только администратору");
    expect(findNodes(tree, (node) => node.type === "form")).toHaveLength(0);
  });
});
