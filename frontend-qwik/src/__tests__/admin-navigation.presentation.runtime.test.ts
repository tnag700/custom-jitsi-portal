/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { beforeEach, describe, expect, it, vi } from "vitest";
import { findNodes, renderNode, textContent } from "./support/jsx-tree";
import AdminLayout from "~/routes/admin/layout";

const state = vi.hoisted(() => ({
  url: "https://portal.example.test/admin/incidents/incident-1?environment=prod",
  claims: ["ROLE_ADMIN"],
  alert: null,
  loaderIndex: 0,
}));

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  const identity = <T>(value: T): T => value;
  return {
    ...actual,
    component$: (render) => (props) => actual.jsx(render, props),
    componentQrl: identity,
    inlinedQrl: identity,
    inlinedQrlDEV: identity,
    qrl: identity,
    Slot: () => actual.jsx("p", { children: "Содержимое раздела" }),
  };
});

vi.mock("@qwik.dev/router", () => {
  const loader = () => {
    const index = state.loaderIndex++;
    return () => ({
      value: index === 0 ? { claims: state.claims } : state.alert,
    });
  };
  return {
    routeLoader$: loader,
    routeLoaderQrl: loader,
    useLocation: () => ({ url: new URL(state.url) }),
  };
});

describe("admin navigation presentation", () => {
  beforeEach(() => {
    state.url =
      "https://portal.example.test/admin/incidents/incident-1?environment=prod";
    state.claims = ["ROLE_ADMIN"];
    state.alert = null;
  });

  it("renders all grouped destinations and highlights only the incident section for a detail page", async () => {
    const tree = await renderNode(AdminLayout({}));
    const navigation = findNodes(tree, (node) => node.type === "nav");
    const links = findNodes(navigation, (node) => node.type === "a");
    const active = links.filter(
      (node) => node.props["aria-current"] === "page",
    );

    expect(navigation).toHaveLength(1);
    expect(navigation[0].props["aria-label"]).toBe("Разделы администрирования");
    expect(links).toHaveLength(9);
    expect(links.some((node) => String(node.props.href).startsWith("/admin/metrics"))).toBe(true);
    expect(active).toHaveLength(1);
    expect(active[0].props.href).toBe("/admin/incidents?environment=prod");
    expect(textContent(active[0])).toContain("Ошибки входа и их разбор");
    expect(textContent(tree)).toContain("Пользователи и доступ");
    expect(textContent(tree)).toContain("Настройки системы");
    expect(links[0].props.href).toBe("#admin-content");
    expect(
      findNodes(tree, (node) => node.props.id === "admin-content")[0].props
        .tabIndex,
    ).toBe(-1);
  });

  it("keeps operator navigation restricted and highlights the trailing-slash overview", async () => {
    state.claims = ["ROLE_SUPPORT-ENGINEER"];
    state.url = "https://portal.example.test/admin/";
    const tree = await renderNode(AdminLayout({}));
    const links = findNodes(tree, (node) => node.type === "a");

    expect(links).toHaveLength(6);
    expect(
      links.some((node) => String(node.props.href).startsWith("/admin/users")),
    ).toBe(false);
    expect(
      links.some((node) => String(node.props.href).startsWith("/admin/jitsi")),
    ).toBe(false);
    expect(links.some((node) => String(node.props.href).startsWith("/admin/metrics"))).toBe(false);
    expect(
      links.find((node) => node.props["aria-current"] === "page")?.props.href,
    ).toBe("/admin");
    expect(textContent(tree)).toContain("Содержимое раздела");
  });

  it("retains critical and update alerts with the selected environment", async () => {
    state.alert = {
      criticalUpdateRequired: true,
      criticalVulnerabilityCount: 2,
      updateAvailableCount: 3,
    };
    const tree = await renderNode(AdminLayout({}));
    const alerts = findNodes(tree, (node) => node.props.role === "alert");

    expect(alerts).toHaveLength(2);
    expect(textContent(alerts)).toContain("Обнаружены критические уязвимости");
    expect(textContent(alerts)).toContain("Доступны новые версии компонентов");
    expect(
      findNodes(alerts, (node) => node.type === "a").map(
        (node) => node.props.href,
      ),
    ).toEqual([
      "/admin/framework-versions?environment=prod",
      "/admin/framework-versions?environment=prod",
    ]);
  });
});
