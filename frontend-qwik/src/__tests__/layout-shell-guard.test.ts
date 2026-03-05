import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Layout Shell Guard: sidebar-nav-items.ts (AC: 2)", () => {
  it("should exist and contain nav items: Кабинет, Комнаты, Встречи, Профиль", () => {
    const ts = readSrc("lib/shared/components/sidebar-nav-items.ts");
    expect(ts).toContain("NavItem");
    expect(ts).toContain("navItems");
    expect(ts).toContain("Кабинет");
    expect(ts).toContain("Комнаты");
    expect(ts).toContain("Встречи");
    expect(ts).toContain("Профиль");
    expect(ts).toContain("/rooms");
    expect(ts).toContain("/meetings");
    expect(ts).toContain("/profile");
  });
});

describe("Layout Shell Guard: Sidebar.tsx (AC: 2, 3, 5)", () => {
  it("should exist and contain component$, <aside>, <nav, aria-label", () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain("component$");
    expect(tsx).toContain("<aside");
    expect(tsx).toContain("<nav");
    expect(tsx).toContain('aria-label');
  });

  it('should contain Link from @qwik.dev/router and useLocation', () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain("Link");
    expect(tsx).toContain("useLocation");
  });

  it('should contain aria-current="page" for active-state', () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain("aria-current");
  });

  it("should use segment-safe active matching and explicit aria-label on nav links", () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain("pathname.startsWith(`${href}/`)");
    expect(tsx).toContain("aria-label={item.label}");
  });

  it("should handle root path matching without false positives", () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain('if (href === "/")');
    expect(tsx).toContain('return pathname === "/";');
  });

  it("should contain collapsed/expanded widths and sidebar toggle labels", () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain('expanded.value ? "w-64" : "w-16"');
    expect(tsx).toContain("Свернуть боковую панель");
    expect(tsx).toContain("Развернуть боковую панель");
  });

  it("should contain focus-visible outline styles", () => {
    const tsx = readSrc("lib/shared/components/Sidebar.tsx");
    expect(tsx).toContain("focus-visible");
    expect(tsx).toContain("outline");
  });
});

describe("Layout Shell Guard: AppHeader.tsx (AC: 4, 6)", () => {
  it("should exist and contain component$, <header>, ThemeToggle", () => {
    const tsx = readSrc("lib/shared/components/AppHeader.tsx");
    expect(tsx).toContain("component$");
    expect(tsx).toContain("<header");
    expect(tsx).toContain("ThemeToggle");
  });

  it('should contain hamburger toggle with aria-label', () => {
    const tsx = readSrc("lib/shared/components/AppHeader.tsx");
    expect(tsx).toContain("aria-label");
    expect(tsx).toContain("Переключить боковую панель");
  });

  it('should contain Войти and Выйти placeholders', () => {
    const tsx = readSrc("lib/shared/components/AppHeader.tsx");
    expect(tsx).toContain("Войти");
    expect(tsx).toContain("Выйти");
  });

  it("should show branding when sidebar is collapsed or user is unauthenticated", () => {
    const tsx = readSrc("lib/shared/components/AppHeader.tsx");
    expect(tsx).toContain("isSidebarExpanded");
    expect(tsx).toContain("!isAuthenticated || !isSidebarExpanded");
  });

  it("should keep unauth login navigation via Link to /auth", () => {
    const tsx = readSrc("lib/shared/components/AppHeader.tsx");
    expect(tsx).toContain("<Link");
    expect(tsx).toContain('href="/auth"');
  });
});

describe("Layout Shell Guard: routes/layout.tsx (AC: 1, 6)", () => {
  it("should contain Sidebar, AppHeader, <main>, useSignal", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("Sidebar");
    expect(tsx).toContain("AppHeader");
    expect(tsx).toContain("<main");
    expect(tsx).toContain("useSignal");
  });

  it("should contain layout shell flex container with design tokens", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("bg-bg");
    expect(tsx).toContain("text-text");
    expect(tsx).toContain("max-w-6xl");
  });

  it("should preserve ThemeProvider (useContextProvider)", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("useContextProvider");
    expect(tsx).toContain("ThemeContext");
    expect(tsx).toContain("routeLoader$");
    expect(tsx).toContain("onRequest");
  });

  it("should resolve auth state on server and avoid hardcoded auth flag", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("AuthContext");
    expect(tsx).toContain("fetchAuthMe");
    expect(tsx).toContain("JSESSIONID");
    expect(tsx).toContain("useAuth");
    expect(tsx).toContain("useContextProvider(AuthContext, authStore)");
    expect(tsx).not.toContain("AUTH_COOKIE");
    expect(tsx).not.toContain("useIsAuthenticated");
    expect(tsx).not.toContain("const isAuthenticated = true;");
  });

  it("should pass explicit header props for auth/unauth branches", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("showSidebarToggle={true}");
    expect(tsx).toContain("showSidebarToggle={false}");
    expect(tsx).toContain("isSidebarExpanded={expanded.value}");
    expect(tsx).toContain("userDisplayName={authStore.profile?.displayName ?? null}");
  });
});
