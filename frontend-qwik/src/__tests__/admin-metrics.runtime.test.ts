import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { eventHandler, findNode, findNodes, renderNode, textContent, type RenderedNode } from "./support/jsx-tree";
import type { Dashboard, MetricDescriptor } from "../lib/domains/admin/admin-metrics.types";
import type * as QwikCore from "@qwik.dev/core";

const mocks = vi.hoisted(() => ({ catalog: vi.fn(), dashboard: vi.fn(), metrics: vi.fn(), save: vi.fn() }));
const state = vi.hoisted(() => ({ stores: [] as unknown[], signals: [] as { value: unknown }[] }));
vi.mock("@qwik.dev/core", async (original) => {
  const actual = await original<typeof QwikCore & { _captures: unknown }>();
  const identity = (value: unknown) => value;
  return { ...actual, component$: identity, componentQrl: identity, get _captures() { return actual._captures; },
    useStore: (value: unknown) => { state.stores.push(value); return value; },
    useSignal: (value: unknown) => { const signal = { value }; state.signals.push(signal); return signal; },
    useVisibleTask$: () => undefined, useVisibleTaskQrl: () => undefined,
  };
});

async function fire(node: RenderedNode | undefined, event: string, ...args: unknown[]) {
  const handler = eventHandler(node, event) as ((...args: unknown[]) => unknown) | undefined;
  return handler?.(...args);
}
vi.mock("@qwik.dev/router", async (original) => ({ ...await original<object>(), routeLoader$: (fn: unknown) => fn, routeLoaderQrl: (fn: unknown) => fn, routeAction$: (fn: unknown) => fn, routeActionQrl: (fn: unknown) => fn }));
vi.mock("~/lib/domains/admin", () => ({ fetchMetricsCatalog: mocks.catalog, fetchMetricDashboard: mocks.dashboard, fetchMetrics: mocks.metrics, saveMetricDashboard: mocks.save, AdminMetricsDashboard: () => null }));

beforeEach(() => { vi.clearAllMocks(); state.stores.length = 0; state.signals.length = 0; });
beforeAll(async () => { await import("../routes/admin/metrics/index"); await import("../lib/domains/admin/components/AdminMetricsDashboard"); });
describe("admin metrics loader", () => {
  it.each([[0, "Нет запросов"], [120_000, "Нет данных"]])("only treats fresh no_traffic readings as no requests (%i ms)", async (age, expected) => {
    const { AdminMetricsDashboard } = await import("../lib/domains/admin/components/AdminMetricsDashboard");
    const qwik = await vi.importActual<typeof QwikCore>("@qwik.dev/core");
    const tree = await renderNode((AdminMetricsDashboard as unknown as (props: unknown) => unknown)({
      catalog: [{ id: "latency", title: "Latency", description: "Показатель", unit: "milliseconds", scope: "SYSTEM", views: ["card", "line"] }],
      dashboard: { revision: 0, period: "1h", widgets: [{ metricId: "latency", view: "line" }] },
      snapshot: { generatedAt: new Date().toISOString(), metrics: [{ id: "latency", value: null, state: "no_traffic", measuredAt: new Date(Date.now() - age).toISOString(), series: [] }] },
      onSave$: qwik.inlinedQrl(vi.fn(), "metrics-no-traffic-test"),
    }));
    const values = findNode(tree, n => n.props["aria-label"] === "Текущие значения");
    expect(textContent(findNodes(values!, n => n.type === "p")[0])).toBe(expected);
    const histories = findNode(tree, n => n.props["aria-label"] === "История показателей");
    expect(textContent(findNode(histories!, n => n.type === "strong"))).toBe(expected);
  });
  it("keeps every selected current value above a separate history area without changing saved order", async () => {
    const { AdminMetricsDashboard } = await import("../lib/domains/admin/components/AdminMetricsDashboard");
    const catalog: MetricDescriptor[] = ["cpu", "memory"].map(id => ({ id, title: id, description: "Показатель", unit: "percent", scope: "SYSTEM", views: ["card", "line"] }));
    const dashboard: Dashboard = { revision: 4, period: "1h", widgets: [{ metricId: "cpu", view: "line" }, { metricId: "memory", view: "card" }] };
    const qwik = await vi.importActual<typeof QwikCore>("@qwik.dev/core");
    const tree = await renderNode((AdminMetricsDashboard as unknown as (props: unknown) => unknown)({
      catalog, dashboard, snapshot: { generatedAt: new Date().toISOString(), metrics: [] }, onSave$: qwik.inlinedQrl(vi.fn(), "metrics-layout-test"),
    }));
    const values = findNode(tree, n => n.props["aria-label"] === "Текущие значения");
    const histories = findNode(tree, n => n.props["aria-label"] === "История показателей");
    expect(values).toBeDefined();
    expect(histories).toBeDefined();
    expect(findNodes(values!, n => n.type === "h2").map(textContent)).toEqual(["cpu", "memory"]);
    expect(findNodes(histories!, n => n.type === "h3").map(textContent)).toEqual(["cpu"]);
    expect(state.stores[0]).toEqual(dashboard);
  });
  it("resets the draft to the initial selection without changing its revision", async () => {
    const { AdminMetricsDashboard } = await import("../lib/domains/admin/components/AdminMetricsDashboard");
    const ids = ["host.cpu", "host.memory", "host.disk", "jvm.heap", "jdbc.pool", "jwt.latency-p95"];
    const catalog: MetricDescriptor[] = ids.map(id => ({ id, title: id, description: "Показатель", unit: "percent", scope: "SYSTEM", views: ["card", "line"] }));
    const save = vi.fn().mockResolvedValue({ status: 200 });
    const qwik = await vi.importActual<typeof QwikCore>("@qwik.dev/core");
    const tree = await renderNode((AdminMetricsDashboard as unknown as (props: unknown) => unknown)({
      catalog, dashboard: { revision: 7, period: "7d", widgets: [] }, snapshot: { generatedAt: "2026-10-02T09:00:00Z", metrics: [] }, onSave$: qwik.inlinedQrl(save, "metrics-reset-test"),
    }));
    const reset = findNode(tree, n => n.type === "button" && textContent(n) === "Сбросить к начальному набору");
    expect(reset).toBeDefined();
    await fire(reset, "click");
    expect(state.stores[0]).toEqual({ revision: 7, period: "1h", widgets: ids.map(metricId => ({ metricId, view: "card" })) });
    expect(save).not.toHaveBeenCalled();
    await fire(findNode(tree, n => n.type === "button" && textContent(n) === "Сохранить дашборд"), "click");
    expect(save).toHaveBeenCalledWith(state.stores[0]);
  });
  it("rejects every non-admin before fetching or serializing detailed data", async () => {
    const { useMetricsDashboard } = await import("../routes/admin/metrics/index");
    for (const role of ["participant", "system-admin", "security-admin", "support-engineer"]) {
      const context = { sharedMap: new Map([["user", { claims: [role] }]]), redirect: (status: number, to: string) => ({ status, to }) };
      await expect((useMetricsDashboard as unknown as (ctx: unknown) => Promise<unknown>)(context)).rejects.toEqual({ status: 302, to: "/" });
    }
    expect(mocks.catalog).not.toHaveBeenCalled();
    expect(mocks.dashboard).not.toHaveBeenCalled();
    expect(mocks.metrics).not.toHaveBeenCalled();
  });

  it("keeps a saved empty selection and avoids querying metrics", async () => {
    const { useMetricsDashboard } = await import("../routes/admin/metrics/index");
    mocks.catalog.mockResolvedValue([]);
    mocks.dashboard.mockResolvedValue({ revision: 3, period: "7d", widgets: [] });
    const context = { sharedMap: new Map<string, unknown>([["user", { claims: ["admin"] }], ["apiUrl", "http://backend/api/v1"]]), cookie: { get: () => undefined }, url: new URL("http://portal/admin/metrics") };
    const result = await (useMetricsDashboard as unknown as (ctx: unknown) => Promise<{ dashboard?: { widgets: unknown[] } }>)(context);
    expect(result.dashboard?.widgets).toEqual([]);
    expect(mocks.metrics).not.toHaveBeenCalled();
  });

  it("renders new catalog entries generically, edits order and retains the draft on 409", async () => {
    const { AdminMetricsDashboard } = await import("../lib/domains/admin/components/AdminMetricsDashboard");
    const catalog: MetricDescriptor[] = ["cpu", "memory", "new"].map(id => ({ id, title: id, description: "Проверенный показатель", unit: "percent", scope: "SYSTEM", views: ["card", "line"] }));
    const save = vi.fn().mockResolvedValue({ status: 409 });
    const qwik = await vi.importActual<typeof QwikCore>("@qwik.dev/core");
    const tree = await renderNode((AdminMetricsDashboard as unknown as (props: unknown) => unknown)({
      catalog, dashboard: { revision: 2, period: "1h", widgets: [{ metricId: "cpu", view: "card" }, { metricId: "memory", view: "card" }] },
      snapshot: { generatedAt: "2026-10-02T09:00:00Z", metrics: [] }, onSave$: qwik.inlinedQrl(save, "metrics-save-test"),
    }));
    const option = findNodes(tree, n => n.type === "option").find(n => n.props.value === "new");
    expect(findNodes(tree, n => n.type === "option").find(n => n.props.value === "1h")?.props.selected).toBe(true);
    expect(textContent(option)).toBe("new");
    const selector = findNodes(tree, n => n.type === "select").find(n => textContent(n).includes("Выберите показатель"));
    await fire(selector, "change", undefined, { value: "new" });
    await fire(findNode(tree, n => n.type === "button" && textContent(n) === "Добавить"), "click");
    const layout = state.stores[0] as Dashboard;
    expect(layout.widgets.map(w => w.metricId)).toEqual(["cpu", "memory", "new"]);
    await fire(findNode(tree, n => n.props["aria-label"] === "Опустить: cpu"), "click");
    expect(layout.widgets.map(w => w.metricId)).toEqual(["memory", "cpu", "new"]);
    const submit = findNode(tree, n => n.type === "button" && textContent(n) === "Сохранить дашборд");
    await fire(submit, "click");
    expect(save).toHaveBeenCalledWith({ revision: 2, period: "1h", widgets: layout.widgets });
    expect(layout.revision).toBe(2);
    expect(layout.widgets.map(w => w.metricId)).toEqual(["memory", "cpu", "new"]);
    expect(state.signals.some(s => typeof s.value === "string" && s.value.includes("Ваши изменения сохранены на этой странице"))).toBe(true);
    await fire(submit, "click");
    expect(save).toHaveBeenCalledTimes(1);
  });
});
