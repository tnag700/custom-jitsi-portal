/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import type {
  AdminRoleHistory,
  AdminRoleHistoryFilters,
} from "~/lib/domains/admin";
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
    RequestStatePanel: (props: Record<string, unknown>) =>
      actual.jsx("div", {
        "data-testid": "request-state",
        children: [props.title, props.detail],
      }),
  };
});

function filters(
  overrides: Partial<AdminRoleHistoryFilters> = {},
): AdminRoleHistoryFilters {
  return {
    environment: "",
    q: "",
    from: "",
    to: "",
    actionType: "",
    role: "",
    actorId: "",
    subjectId: "",
    roomId: "",
    meetingId: "",
    page: "0",
    pageSize: "20",
    returnTo: "",
    ...overrides,
  };
}

function history(): AdminRoleHistory {
  return {
    tenantId: "tenant-1",
    environment: "prod",
    generatedAt: "2026-07-30T09:00:00Z",
    page: 1,
    pageSize: 20,
    totalElements: 42,
    totalPages: 3,
    content: [
      {
        occurredAt: "2026-07-30T08:45:00Z",
        actionType: "update",
        actionLabel: "Роль изменена",
        oldRole: "participant",
        newRole: "moderator",
        subjectLabel: "Иван Иванов",
        subjectReference: "subject-1",
        actorLabel: "Администратор",
        actorReference: "admin-1",
        tenantId: "tenant-1",
        environment: "prod",
        roomId: "room-1",
        meetingId: "meeting-1",
        traceId: "trace-1",
      },
    ],
  };
}

describe("admin role history presentation", () => {
  it("keeps the initial state bounded and advanced controls collapsed", async () => {
    const { AdminRoleHistoryOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const tree = await renderNode(
      AdminRoleHistoryOverview({
        currentUrl: "http://localhost:3000/admin/role-history/",
        history: null,
        hasPrimaryFilter: false,
        filters: filters(),
      }),
    );
    const content = textContent(tree);
    const details = findNodes(tree, (node) => node.type === "details");

    expect(content).toContain("Сначала задайте область поиска");
    expect(content).toContain("Дополнительные фильтры");
    expect(details[0]?.props.open).toBe(false);
    expect(content).not.toContain("Лента событий");
  });

  it("renders localized transitions and keeps identifiers behind disclosure", async () => {
    const { AdminRoleHistoryOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const tree = await renderNode(
      AdminRoleHistoryOverview({
        currentUrl:
          "http://localhost:3000/admin/role-history/?subjectId=subject-1&page=1",
        history: history(),
        hasPrimaryFilter: true,
        filters: filters({ subjectId: "subject-1", page: "1" }),
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");

    expect(content).toContain("Участник → Модератор");
    expect(content).toContain("Технический контекст");
    expect(content).toContain("найдено 42");
    expect(
      links.find((link) => textContent(link).includes("Предыдущая"))?.props
        .href,
    ).toContain("page=0");
    expect(
      links.find((link) => textContent(link).includes("Следующая"))?.props.href,
    ).toContain("page=2");
  });
});
