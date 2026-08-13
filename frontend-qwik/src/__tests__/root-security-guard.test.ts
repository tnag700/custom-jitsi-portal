import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type { DocumentHeadProps, DocumentHeadValue } from "@qwik.dev/router";
import { head } from "../routes/layout";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

function resolveLayoutHead(href: string): DocumentHeadValue {
  if (typeof head !== "function") {
    throw new Error("Layout head must be a dynamic resolver");
  }

  return head({
    url: new URL(href),
  } as DocumentHeadProps);
}

describe("Root Security Guard", () => {
  it("root.tsx should not rely on inline script bootstrap", () => {
    const tsx = readSrc("root.tsx");
    expect(tsx).not.toContain("dangerouslySetInnerHTML");
    expect(tsx).not.toContain("<script");
  });

  it("root.tsx should preserve document head and router shell", () => {
    const tsx = readSrc("root.tsx");
    expect(tsx).toContain("DocumentHeadTags");
    expect(tsx).toContain("RouterOutlet");
    expect(tsx).toContain("useQwikRouter");
    expect(tsx).not.toContain("useLocation");
    expect(tsx).not.toContain("resolveCanonicalHref");
  });

  it("routes canonical metadata through the layout head resolver", () => {
    const tsx = readSrc("routes/layout.tsx");
    expect(tsx).toContain("export const head: DocumentHead");
    expect(tsx).toContain("resolveCanonicalHref(url)");
    expect(tsx).toContain('rel: "canonical"');
  });

  it.each([
    "/invite/secret-token",
    "/INVITE/secret-token",
    "/%69nvite/secret-token",
    "/%2569nvite/secret-token",
    "//invite/secret-token",
  ])("omits canonical metadata for sensitive route %s", (pathname) => {
    const result = resolveLayoutHead(`https://portal.example${pathname}`);

    expect(result.links).toBeUndefined();
  });

  it("returns exactly one keyed canonical link for a regular route", () => {
    const result = resolveLayoutHead(
      "https://portal.example/meetings/?roomId=room-1#local",
    );

    expect(result.links).toEqual([
      {
        key: "canonical",
        rel: "canonical",
        href: "https://portal.example/meetings/?roomId=room-1",
      },
    ]);
  });
});
