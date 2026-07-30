/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import type { AdminDashboardSummary } from "~/lib/domains/admin";
import { findNodes, renderNode, textContent } from "./support/jsx-tree";

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

function createDashboard(): AdminDashboardSummary {
  const healthyHandoff = {
    environment: "dev",
    period: "15m",
    severity: "info",
    errorCode: null,
    category: "CONFIG",
    roomId: null,
    meetingId: null,
    incidentId: null,
  };

  return {
    period: "15m",
    environment: "dev",
    tenantId: "tenant-1",
    generatedAt: "2026-07-29T12:00:00Z",
    traceId: "trace-admin-1",
    priorityBanner: {
      active: false,
      severity: "info",
      headline: "Операционный контур стабилен",
      summary: "Активных деградаций не обнаружено.",
      actionLabel: "Открыть очередь инцидентов",
      handoff: healthyHandoff,
    },
    topDegradations: [],
    keyServiceStatuses: [
      {
        key: "portal",
        label: "Portal",
        status: "UP",
        detail: "Portal is healthy.",
        handoff: healthyHandoff,
      },
      {
        key: "backend",
        label: "Backend API",
        status: "READY",
        detail: "Backend is ready.",
        handoff: healthyHandoff,
      },
    ],
    latestSpikes: [],
    affectedScopeSummary: [],
    safeStateSummary: {
      stable: true,
      headline: "Система стабильна",
      summary: "Контрольные переходы доступны оператору.",
      actions: [
        {
          label: "История ролей",
          href: "/admin/role-history?environment=dev",
        },
      ],
      recentResolvedSpikes: [],
    },
    entityFilter: {
      roomId: null,
      meetingId: null,
    },
    sampleWindowLimited: false,
  };
}

const filters = {
  period: "15m",
  environment: "",
  roomId: "",
  meetingId: "",
  errorCode: "",
  category: "",
};

describe("admin dashboard presentation", () => {
  it("keeps stable state compact and sends its primary action to the incident queue", async () => {
    const { AdminDashboardOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const tree = renderNode(
      AdminDashboardOverview({
        currentUrl: "http://localhost:3000/admin",
        dashboard: createDashboard(),
        drillDown: null,
        drillDownError: null,
        filters,
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");
    const priorityAction = links.find(
      (link) => textContent(link) === "Открыть очередь инцидентов",
    );

    expect(content).toContain("Состояние платформы");
    expect(content).toContain("2 из 2 в норме");
    expect(content).toContain("Активных деградаций нет");
    expect(content).toContain("Сигнал не выбран");
    expect(content).not.toContain("Новые всплески");
    expect(content).not.toContain("Затронутый контур");
    expect(content).not.toContain("Дополнительные действия");
    expect(priorityAction?.props.href).toBe(
      "/admin/incidents?environment=dev&period=15m",
    );
  });

  it("renders active evidence, bounded drill-down details, and normalized handoff links", async () => {
    const { AdminDashboardOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const dashboard = createDashboard();
    const activeHandoff = {
      environment: "prod",
      period: "1h",
      severity: "critical",
      errorCode: "JOIN_BLOCKED",
      category: "AUTH",
      roomId: "room-1",
      meetingId: "meeting-1",
      incidentId: null,
    };
    dashboard.priorityBanner = {
      active: true,
      severity: "critical",
      headline: "Вход во встречи заблокирован",
      summary: "Нужен разбор свежих отказов.",
      actionLabel: "Разобрать сигнал",
      handoff: activeHandoff,
    };
    dashboard.topDegradations = [
      {
        id: "join-blocked",
        title: "Join flow degraded",
        summary: "Authentication failures are elevated.",
        severity: "critical",
        actionLabel: "Открыть сигнал",
        handoff: activeHandoff,
      },
    ];
    dashboard.latestSpikes = [
      {
        errorCode: "JOIN_BLOCKED",
        category: "AUTH",
        count: 4,
        summary: "Four recent failures.",
        handoff: activeHandoff,
      },
    ];
    dashboard.affectedScopeSummary = [
      {
        scopeType: "room",
        scopeValue: "room-1",
        affectedAttempts: 4,
        summary: "One room is affected.",
        handoff: activeHandoff,
      },
    ];
    dashboard.safeStateSummary.stable = false;

    const tree = renderNode(
      AdminDashboardOverview({
        currentUrl: "http://localhost:3000/admin?environment=prod&period=1h",
        dashboard,
        drillDown: {
          period: "1h",
          environment: "prod",
          tenantId: "tenant-1",
          generatedAt: "2026-07-29T12:00:00Z",
          selectionType: "errorCode",
          selectionValue: "JOIN_BLOCKED",
          entityFilter: {
            roomId: "room-1",
            meetingId: "meeting-1",
          },
          failureCount: 4,
          recentSamples: [
            {
              occurredAt: "2026-07-29T11:58:00Z",
              roomId: "room-1",
              meetingId: "meeting-1",
              subjectId: "subject-1",
              traceId: "trace-1",
              traceUrl: "https://traces.example.test/trace-1",
              errorCode: "JOIN_BLOCKED",
              reasonCategory: "AUTH",
              userMessage: "Join denied",
            },
          ],
          sampleWindowLimited: false,
        },
        drillDownError: null,
        filters: {
          ...filters,
          period: "1h",
          environment: "prod",
        },
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");
    const priorityAction = links.find(
      (link) => textContent(link) === "Разобрать сигнал",
    );

    expect(content).toContain("Требует внимания");
    expect(content).toContain("Активные деградации");
    expect(content).toContain("Новые всплески");
    expect(content).toContain("Затронутый контур");
    expect(content).toContain("errorCode: JOIN_BLOCKED");
    expect(content).toContain("Join denied");
    expect(content).toContain("ID комнаты");
    expect(content).toContain("ID встречи");
    expect(content).toContain("Трассировка");
    expect(priorityAction?.props.href).toContain(
      "/admin?environment=prod&period=1h&errorCode=JOIN_BLOCKED&category=AUTH&roomId=room-1&meetingId=meeting-1",
    );
  });
});
