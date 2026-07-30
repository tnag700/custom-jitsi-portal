/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import type {
  AdminIncidentList,
  AdminIncidentSearch,
  IncidentQueueFilters,
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

vi.mock("~/lib/shared", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    RequestStatePanel: (props: Record<string, unknown>) =>
      actual.jsx("div", {
        "data-testid": "request-state",
        children: [props.title, props.detail],
      }),
  };
});

function createFilters(
  overrides: Partial<IncidentQueueFilters> = {},
): IncidentQueueFilters {
  return {
    period: "15m",
    environment: "",
    view: "",
    facet: "",
    roomId: "",
    meetingId: "",
    subjectId: "",
    errorCode: "",
    category: "",
    severity: "",
    traceId: "",
    requestId: "",
    from: "",
    to: "",
    limit: "50",
    offset: "0",
    ...overrides,
  };
}

function createIncidents(): AdminIncidentList {
  return {
    period: "15m",
    environment: "dev",
    tenantId: "tenant-1",
    generatedAt: "2026-07-29T12:00:00Z",
    selectedView: "active",
    selectedQuickFacet: null,
    availableViews: [
      {
        token: "active",
        label: "Active",
        summary: "Свежие и незакрытые сигналы.",
      },
      {
        token: "critical",
        label: "Critical",
        summary: "Критичные сигналы.",
      },
    ],
    quickFacets: [
      {
        token: "warning",
        label: "Warn",
        count: 2,
        active: false,
      },
      {
        token: "config",
        label: "Config",
        count: 1,
        active: false,
      },
    ],
    sort: {
      token: "severity-freshness",
      label: "Severity + freshness",
      direction: "desc",
    },
    pageSize: 50,
    offset: 0,
    totalElements: 0,
    items: [],
  };
}

describe("admin incidents presentation", () => {
  it("keeps an empty queue compact, query-driven and free of duplicate admin navigation", async () => {
    const { AdminIncidentQueueOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const tree = renderNode(
      AdminIncidentQueueOverview({
        currentUrl:
          "http://localhost:3000/admin/incidents?period=15m&environment=dev",
        incidents: createIncidents(),
        searchResult: null,
        filters: createFilters({ environment: "dev" }),
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");
    const details = findNode(tree, (node) => node.type === "details");
    const emptyState = findNode(
      tree,
      (node) => node.props["data-testid"] === "request-state",
    );

    expect(content).toContain("Инциденты входа");
    expect(content).toContain("0 в очереди");
    expect(content).toContain("Свежие и незакрытые сигналы.");
    expect(content).toContain("Очередь пуста для выбранного режима");
    expect(content).not.toContain("Вторичные модули");
    expect(content).not.toContain("Дополнительные административные разделы");
    expect(details?.props.open).toBe(false);
    expect(emptyState).toBeDefined();
    expect(
      links.find((link) => textContent(link) === "Critical")?.props.href,
    ).toBe(
      "/admin/incidents?period=15m&environment=dev&view=critical&offset=0",
    );
    expect(
      links.find((link) => textContent(link).includes("Warn"))?.props.href,
    ).toBe(
      "/admin/incidents?period=15m&environment=dev&view=active&facet=warning&offset=0",
    );
  });

  it("renders incident and search candidates with effective environment context", async () => {
    const { AdminIncidentQueueOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const incidents = createIncidents();
    incidents.totalElements = 1;
    incidents.items = [
      {
        incidentId: "incident-1",
        occurredAt: "2026-07-29T11:58:00Z",
        errorCode: "JOIN_BLOCKED",
        category: "AUTH",
        tenantId: "tenant-1",
        roomId: "room-1",
        meetingId: "meeting-1",
        affectedSubjects: 3,
        severity: "critical",
        affectedEntitySummary: "Комната room-1, 3 пользователя",
        freshnessHint: "",
      },
    ];
    const searchResult: AdminIncidentSearch = {
      outcome: "candidates",
      incidentId: null,
      message: "Найдены близкие совпадения.",
      candidates: [
        {
          incidentId: "incident-2",
          occurredAt: "2026-07-29T11:50:00Z",
          errorCode: "TOKEN_EXPIRED",
        },
      ],
    };
    const tree = renderNode(
      AdminIncidentQueueOverview({
        currentUrl:
          "http://localhost:3000/admin/incidents?period=15m&traceId=trace-1",
        incidents,
        searchResult,
        filters: createFilters({ traceId: "trace-1" }),
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");
    const details = findNode(tree, (node) => node.type === "details");

    expect(content).toContain("Найдены близкие совпадения.");
    expect(content).toContain("Комната room-1, 3 пользователя");
    expect(content).toContain("Сводка активности недоступна");
    expect(details?.props.open).toBe(true);
    expect(
      links.find((link) => textContent(link).includes("JOIN_BLOCKED"))?.props
        .href,
    ).toContain("/admin/incidents/incident-1?environment=dev");
    expect(
      links.find((link) => textContent(link).includes("TOKEN_EXPIRED"))?.props
        .href,
    ).toContain("/admin/incidents/incident-2?environment=dev");
  });

  it("preserves advanced filter values and resets only the refinement layer", async () => {
    const { AdminIncidentQueueOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const filters = createFilters({
      environment: "prod",
      view: "critical",
      facet: "warning",
      category: "AUTH",
      meetingId: "meeting-7",
      requestId: "request-7",
      limit: "25",
      offset: "25",
    });
    const incidents = createIncidents();
    incidents.environment = "prod";
    incidents.selectedView = "critical";
    incidents.selectedQuickFacet = "warning";
    incidents.quickFacets[0].active = true;
    const tree = renderNode(
      AdminIncidentQueueOverview({
        currentUrl:
          "http://localhost:3000/admin/incidents?environment=prod&view=critical&facet=warning&category=AUTH&meetingId=meeting-7&requestId=request-7&limit=25&offset=25",
        incidents,
        searchResult: null,
        filters,
      }),
    );
    const inputs = findNodes(
      tree,
      (node) => node.type === "input" || node.type === "select",
    );
    const links = findNodes(tree, (node) => node.type === "a");
    const valueFor = (name: string) =>
      inputs.find((node) => node.props.name === name)?.props.value;

    expect(valueFor("category")).toBe("AUTH");
    expect(valueFor("meetingId")).toBe("meeting-7");
    expect(valueFor("requestId")).toBe("request-7");
    expect(valueFor("limit")).toBe("25");
    expect(valueFor("offset")).toBe("0");
    expect(
      links.find((link) => textContent(link) === "Сбросить уточнения")?.props
        .href,
    ).toBe(
      "/admin/incidents?environment=prod&view=critical&facet=warning&limit=25&offset=0&period=15m",
    );
  });
});
