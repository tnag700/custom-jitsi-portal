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
    expect(tsx).toContain("rel=\"canonical\"");
  });
});