import { describe, expect, it } from "vitest";
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

describe("Shared Error UI Guard", () => {
  it("ApiErrorAlert should exist and compose RequestStatePanel", () => {
    const tsx = readSrc("lib/shared/components/ApiErrorAlert.tsx");
    expect(tsx).toContain("component$");
    expect(tsx).toContain("RequestStatePanel");
    expect(tsx).toContain("errorCode");
    expect(tsx).toContain("traceId");
  });

  it("shared components barrel should export ApiErrorAlert", () => {
    const ts = readSrc("lib/shared/components/index.ts");
    expect(ts).toContain("ApiErrorAlert");
  });

  it("AuthErrorPanel should compose shared RequestStatePanel", () => {
    const tsx = readSrc("lib/domains/auth/components/AuthErrorPanel.tsx");
    expect(tsx).toContain("RequestStatePanel");
    expect(tsx).toContain("Код ошибки");
    expect(tsx).toContain("Попробовать снова");
  });
});
