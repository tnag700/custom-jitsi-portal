/* eslint-disable @typescript-eslint/ban-ts-comment */
// @ts-nocheck
import { describe, expect, it, vi } from "vitest";
import {
  collectNodes,
  findNode,
  findNodes,
  isRenderedNode,
  renderNode,
  textContent,
} from "./support/jsx-tree";

let currentPathname = "/";

vi.mock("@qwik.dev/core", async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    $: <T>(value: T): T => value,
    inlinedQrl: <T>(value: T): T => value,
    inlinedQrlDEV: <T>(value: T): T => value,
    qrl: <T>(value: T): T => value,
    component$:
      <TProps extends object>(render: (props: TProps) => unknown) =>
      (props: TProps) =>
        actual.jsx(render as never, props as never),
    componentQrl: <T>(value: T): T => value,
  };
});

vi.mock("@qwik.dev/router", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    Form: (props: Record<string, unknown>) => actual.jsx("form", props),
    Link: (props: Record<string, unknown>) => actual.jsx("a", props),
    useLocation: () => ({
      url: new URL(`http://localhost:3000${currentPathname}`),
    }),
  };
});

vi.mock("~/lib/shared/components/ThemeToggle", async () => {
  const actual = await import("@qwik.dev/core");
  return {
    ThemeToggle: () =>
      actual.jsx("button", {
        type: "button",
        "data-testid": "theme-toggle",
        children: "Тема",
      }),
  };
});

function classText(node: { props: Record<string, unknown> }): string {
  return JSON.stringify(node.props.class ?? "");
}

describe("layout shell runtime", () => {
  it("defines stable navigation and segment-safe active matching", async () => {
    const { isNavItemActive, navItems } = await import(
      "~/lib/shared/components/sidebar-nav-items"
    );

    expect(navItems.map((item) => [item.label, item.href])).toEqual([
      ["Кабинет", "/"],
      ["Комнаты", "/rooms"],
      ["Встречи", "/meetings"],
      ["Профиль", "/profile"],
      ["Админ", "/admin"],
    ]);
    expect(isNavItemActive("/", "/")).toBe(true);
    expect(isNavItemActive("/rooms", "/rooms")).toBe(true);
    expect(isNavItemActive("/rooms/active", "/rooms")).toBe(true);
    expect(isNavItemActive("/rooms-archive", "/rooms")).toBe(false);
    expect(isNavItemActive("/profile", "/")).toBe(false);
  });

  it("renders collapsed desktop navigation with active and accessible links", async () => {
    currentPathname = "/rooms/active";
    const { Sidebar } = await import("~/lib/shared/components/Sidebar");
    const expanded = { value: false };

    const tree = renderNode(Sidebar({ expanded }));
    expect(isRenderedNode(tree)).toBe(true);

    const aside = findNode(tree, (node) => node.type === "aside");
    const nav = findNode(tree, (node) => node.type === "nav");
    const links = findNodes(tree, (node) => node.type === "a");
    const roomsLink = links.find(
      (node) => node.props["aria-label"] === "Комнаты",
    );
    const toggle = findNode(
      tree,
      (node) =>
        node.type === "button" &&
        node.props["aria-label"] === "Развернуть боковую панель",
    );

    expect(aside?.props.id).toBe("desktop-navigation");
    expect(aside?.props["aria-label"]).toBe("Боковая панель");
    expect(classText(aside!)).toContain("lg:block");
    expect(classText(aside!)).toContain("lg:w-16");
    expect(nav?.props["aria-label"]).toBe("Основная навигация");
    expect(roomsLink?.props["aria-current"]).toBe("page");
    expect(roomsLink?.props.prefetch).toBe("js");
    expect(roomsLink?.props.title).toBe("Комнаты");
    expect(toggle).toBeDefined();
  });

  it("renders mobile navigation as a dismissible off-canvas panel", async () => {
    currentPathname = "/meetings";
    const { Sidebar } = await import("~/lib/shared/components/Sidebar");
    const dismiss = vi.fn();

    const tree = renderNode(
      Sidebar({
        expanded: { value: false },
        variant: "mobile",
        onDismiss$: dismiss,
      }),
    );
    const aside = findNode(tree, (node) => node.type === "aside");
    const links = findNodes(tree, (node) => node.type === "a");
    const close = findNode(
      tree,
      (node) =>
        node.type === "button" && node.props["aria-label"] === "Закрыть меню",
    );

    expect(aside?.props.id).toBe("mobile-navigation");
    expect(aside?.props["aria-label"]).toBe("Мобильное меню");
    expect(classText(aside!)).toContain("fixed inset-y-0 left-0");
    expect(textContent(tree)).toContain("Jitsi Portal");
    expect(links.every((link) => link.props.onClick$ === dismiss)).toBe(true);
    expect(close?.props.onClick$).toBe(dismiss);
  });

  it("exposes mobile menu state and authenticated controls in the header", async () => {
    const qwikHarnessWarning = vi
      .spyOn(console, "warn")
      .mockImplementation(() => undefined);
    const { AppHeader } = await import("~/lib/shared/components/AppHeader");
    const toggle = vi.fn();
    const logoutAction = { actionPath: "/logout" };
    const tree = renderNode(
      AppHeader({
        isAuthenticated: true,
        showSidebarToggle: true,
        isSidebarExpanded: true,
        isMobileSidebarOpen: true,
        userDisplayName: "Dev Admin",
        logoutAction,
        onToggleSidebar$: toggle,
      }),
    );

    const header = findNode(tree, (node) => node.type === "header");
    const menuButton = findNode(
      tree,
      (node) =>
        node.type === "button" &&
        node.props["aria-controls"] === "mobile-navigation",
    );
    const form = findNode(tree, (node) => node.type === "form");
    const submit = findNode(
      tree,
      (node) => node.type === "button" && node.props.type === "submit",
    );

    expect(header).toBeDefined();
    expect(menuButton?.props["aria-label"]).toBe("Закрыть меню");
    expect(menuButton?.props["aria-expanded"]).toBe(true);
    expect(menuButton?.props.onClick$).toBe(toggle);
    expect(form?.props.action).toBe(logoutAction);
    expect(textContent(submit)).toBe("Выйти");
    expect(textContent(tree)).toContain("Dev Admin");
    qwikHarnessWarning.mockRestore();
  });

  it("renders unauthenticated login navigation without a sidebar toggle", async () => {
    const { AppHeader } = await import("~/lib/shared/components/AppHeader");
    const tree = renderNode(
      AppHeader({
        isAuthenticated: false,
        showSidebarToggle: false,
        isSidebarExpanded: false,
        onToggleSidebar$: vi.fn(),
      }),
    );

    const links = findNodes(tree, (node) => node.type === "a");
    expect(links).toHaveLength(1);
    expect(links[0]?.props.href).toBe("/auth");
    expect(textContent(links[0])).toBe("Войти");
    expect(
      collectNodes(tree).some(
        (node) => node.props["aria-controls"] === "mobile-navigation",
      ),
    ).toBe(false);
  });
});
