/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import type {
  AdminConfigRouteFilters,
  AdminConfigSetCapability,
  AdminConfigSetDetail,
  AdminConfigSetSummary,
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

function createFilters(
  overrides: Partial<AdminConfigRouteFilters> = {},
): AdminConfigRouteFilters {
  return {
    environment: "",
    status: "",
    mode: "",
    configSetId: "cfg-1",
    returnTo: "",
    ...overrides,
  };
}

function createCapability(
  overrides: Partial<AdminConfigSetCapability> = {},
): AdminConfigSetCapability {
  return {
    role: "admin",
    canMutate: true,
    reason: null,
    ...overrides,
  };
}

function createDetail(): AdminConfigSetDetail {
  return {
    configSetId: "cfg-1",
    name: "Primary DEV",
    tenantId: "tenant-1",
    environmentType: "DEV",
    issuer: "https://issuer.example.test",
    audience: "jitsi-meet",
    algorithm: "HS256",
    roleClaim: "role",
    signingSecret: "***",
    jwksUri: null,
    accessTtlMinutes: 20,
    refreshTtlMinutes: 60,
    meetingsServiceUrl: "https://meetings.example.test/api/v1",
    status: "active",
    createdAt: "2026-07-29T10:00:00Z",
    updatedAt: "2026-07-29T11:00:00Z",
    compatibility: {
      status: "COMPATIBLE",
      checkedAt: "2026-07-29T11:05:00Z",
      traceId: "trace-compat-1",
      mismatches: [],
    },
    latestRollout: {
      rolloutId: "rollout-1",
      configSetId: "cfg-1",
      previousConfigSetId: null,
      tenantId: "tenant-1",
      environmentType: "DEV",
      status: "SUCCEEDED",
      validationErrors: null,
      startedAt: "2026-07-29T10:30:00Z",
      completedAt: "2026-07-29T10:31:00Z",
      actorId: "alice.admin",
    },
  };
}

function createSummary(detail = createDetail()): AdminConfigSetSummary {
  return {
    ...detail,
    latestRollout: detail.latestRollout,
    compatibilityStatus: detail.compatibility?.status ?? null,
    compatibilityTraceId: detail.compatibility?.traceId ?? null,
    capability: createCapability(),
  };
}

function createAction(isRunning = false) {
  return {
    isRunning,
    value: undefined,
  };
}

describe("admin config presentation", () => {
  it("marks the normalized environment as selected in SSR output", async () => {
    const { AdminConfigSetsToolbar } = await import(
      "~/lib/domains/admin/components/AdminConfigSetsToolbar"
    );
    const tree = await renderNode(
      AdminConfigSetsToolbar({
        currentUrl: "http://localhost:3000/admin/config-sets/?environment=dev",
        capability: createCapability(),
        filters: createFilters({ environment: "DEV" }),
      }),
    );
    const options = findNodes(tree, (node) => node.type === "option");

    expect(
      options.find((option) => option.props.value === "DEV")?.props.selected,
    ).toBe(true);
    expect(
      options.find((option) => option.props.value === "")?.props.selected,
    ).toBe(false);
  });

  it("renders a compact view workspace without duplicating the admin shell", async () => {
    const { AdminConfigSetsOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const detail = createDetail();
    const tree = await renderNode(
      AdminConfigSetsOverview({
        currentUrl:
          "http://localhost:3000/admin/config-sets/?configSetId=cfg-1",
        items: [createSummary(detail)],
        selectedConfig: detail,
        capability: createCapability(),
        filters: createFilters(),
        saveAction: createAction(),
        compatibilityAction: createAction(),
        rolloutAction: createAction(),
        rollbackAction: createAction(),
        activeOperation: null,
        activeError: null,
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");
    const buttons = findNodes(tree, (node) => node.type === "button");
    const listLink = links.find(
      (link) => link.props.href === "/admin/config-sets/?configSetId=cfg-1",
    );

    expect(content).toContain("Конфиг-наборы");
    expect(content).toContain("Роль admin");
    expect(content).toContain("Совместимость");
    expect(content).toContain("Развёртывание");
    expect(content).not.toContain("Вторичный модуль");
    expect(content).not.toContain("Следующий этап раздела");
    expect(content).not.toContain("Вернуться к очереди инцидентов");
    expect(content).not.toContain("Сохранить изменения");
    expect(textContent(listLink)).not.toContain("Issuer:");
    expect(
      links.find((link) => textContent(link) === "Редактировать")?.props.href,
    ).toBe("/admin/config-sets/?configSetId=cfg-1&mode=edit");
    expect(
      buttons.find(
        (button) => textContent(button) === "Запустить развёртывание",
      )?.props.disabled,
    ).toBe(false);
  });

  it("enters an explicit edit mode and preserves form values", async () => {
    const { AdminConfigSetsOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const detail = createDetail();
    const tree = await renderNode(
      AdminConfigSetsOverview({
        currentUrl:
          "http://localhost:3000/admin/config-sets/?configSetId=cfg-1&mode=edit",
        items: [createSummary(detail)],
        selectedConfig: detail,
        capability: createCapability(),
        filters: createFilters({ mode: "edit" }),
        saveAction: createAction(),
        compatibilityAction: createAction(),
        rolloutAction: createAction(),
        rollbackAction: createAction(),
        activeOperation: null,
        activeError: null,
      }),
    );
    const content = textContent(tree);
    const controls = findNodes(
      tree,
      (node) => node.type === "input" || node.type === "select",
    );
    const valueFor = (name: string) =>
      controls.find((node) => node.props.name === name)?.props.value;

    expect(content).toContain("Редактирование набора");
    expect(content).toContain("Сохранить изменения");
    expect(valueFor("name")).toBe("Primary DEV");
    expect(valueFor("issuer")).toBe("https://issuer.example.test");
    expect(valueFor("configSetId")).toBe("cfg-1");
  });

  it("keeps create mode useful for an empty list and shows an explicit return context only when provided", async () => {
    const { AdminConfigSetsOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const tree = await renderNode(
      AdminConfigSetsOverview({
        currentUrl:
          "http://localhost:3000/admin/config-sets/?mode=create&returnTo=%2Fadmin%2Fincidents%3Fenvironment%3Ddev",
        items: [],
        selectedConfig: null,
        capability: createCapability(),
        filters: createFilters({
          mode: "create",
          configSetId: "",
          returnTo: "/admin/incidents?environment=dev",
        }),
        saveAction: createAction(),
        compatibilityAction: createAction(),
        rolloutAction: createAction(),
        rollbackAction: createAction(),
        activeOperation: null,
        activeError: null,
      }),
    );
    const content = textContent(tree);
    const links = findNodes(tree, (node) => node.type === "a");
    const emptyState = findNode(
      tree,
      (node) => node.props["data-testid"] === "request-state",
    );

    expect(content).toContain("Новый конфиг-набор");
    expect(content).toContain("Создать набор");
    expect(content).toContain("Вернуться к инцидентам");
    expect(emptyState).toBeDefined();
    expect(
      links.find((link) => textContent(link) === "Вернуться к инцидентам")
        ?.props.href,
    ).toBe("/admin/incidents?environment=dev");
  });

  it("blocks rollout for an incompatible config while keeping rollback available to admin", async () => {
    const { AdminConfigSetsOverview } = await import(
      "~/lib/domains/admin/components"
    );
    const detail = createDetail();
    detail.compatibility = {
      status: "INCOMPATIBLE",
      checkedAt: "2026-07-29T11:05:00Z",
      traceId: "trace-incompatible",
      mismatches: [
        {
          code: "ISSUER_MISMATCH",
          message: "Issuer mismatch",
          expected: "issuer-a",
          actual: "issuer-b",
        },
      ],
    };
    const tree = await renderNode(
      AdminConfigSetsOverview({
        currentUrl:
          "http://localhost:3000/admin/config-sets/?configSetId=cfg-1",
        items: [createSummary(detail)],
        selectedConfig: detail,
        capability: createCapability(),
        filters: createFilters(),
        saveAction: createAction(),
        compatibilityAction: createAction(),
        rolloutAction: createAction(),
        rollbackAction: createAction(),
        activeOperation: null,
        activeError: null,
      }),
    );
    const buttons = findNodes(tree, (node) => node.type === "button");

    expect(
      buttons.find(
        (button) => textContent(button) === "Запустить развёртывание",
      )?.props.disabled,
    ).toBe(true);
    expect(
      buttons.find((button) => textContent(button) === "Выполнить откат")?.props
        .disabled,
    ).toBe(false);
  });
});
