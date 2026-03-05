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

describe("Design System Guard: design-tokens.css", () => {
  it("should exist and contain :root with mandatory tokens", () => {
    const css = readSrc("lib/shared/styles/design-tokens.css");
    expect(css).toContain(":root");
    expect(css).toContain(".dark");
    for (const token of ["--bg", "--surface", "--text", "--primary"]) {
      expect(css).toContain(token);
    }
  });
});

describe("Design System Guard: global.css", () => {
  it("should contain @custom-variant dark and @theme block with Tailwind mappings", () => {
    const css = readSrc("global.css");
    expect(css).toContain("@custom-variant dark");
    expect(css).toContain("@theme");
    expect(css).toContain("--color-bg");
    expect(css).toContain("--color-surface");
    expect(css).toContain('@import "./lib/shared/styles/design-tokens.css"');
  });
});

describe("Design System Guard: ThemeToggle.tsx", () => {
  it('should exist and contain role="switch", aria-label, useContext', () => {
    const tsx = readSrc("lib/shared/components/ThemeToggle.tsx");
    expect(tsx).toContain('role="switch"');
    expect(tsx).toContain("aria-label");
    expect(tsx).toContain("useContext");
  });
});

describe("Design System Guard: theme-context.ts", () => {
  it("should exist and contain createContextId and cookie", () => {
    const ts = readSrc("lib/shared/stores/theme-context.ts");
    expect(ts).toContain("createContextId");
    expect(ts).toContain("cookie");
  });
});
