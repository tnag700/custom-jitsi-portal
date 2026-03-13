import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");
const PROJECT_DIR = join(SRC_DIR, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

function readProject(relativePath: string): string {
  const full = join(PROJECT_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("API Client Guard: dependencies", () => {
  it("openapi-typescript should exist in devDependencies", () => {
    const pkg = JSON.parse(readProject("package.json")) as {
      devDependencies?: Record<string, string>;
    };
    expect(pkg.devDependencies?.["openapi-typescript"]).toBeDefined();
  });

  it("openapi-fetch should exist in dependencies", () => {
    const pkg = JSON.parse(readProject("package.json")) as {
      dependencies?: Record<string, string>;
    };
    expect(pkg.dependencies?.["openapi-fetch"]).toBeDefined();
  });

  it("generate:api script should exist", () => {
    const pkg = JSON.parse(readProject("package.json")) as {
      scripts?: Record<string, string>;
    };
    expect(pkg.scripts?.["generate:api"]).toBeDefined();
  });
});

describe("API Client Guard: structure", () => {
  it("generated api-types.ts should exist and contain paths/components", () => {
    const ts = readSrc("lib/shared/api/generated/api-types.ts");
    expect(ts).toContain("interface paths");
    expect(ts).toContain("interface components");
  });

  it("client.ts should contain createClient", () => {
    const ts = readSrc("lib/shared/api/client.ts");
    expect(ts).toContain("createClient");
    expect(ts).toContain("openapi-fetch");
  });

  it("helpers.ts should contain adaptProblemDetails", () => {
    const ts = readSrc("lib/shared/api/helpers.ts");
    expect(ts).toContain("adaptProblemDetails");
  });

  it("responses.ts should export core schemas", () => {
    const ts = readSrc("lib/shared/api/schemas/responses.ts");
    expect(ts).toContain("roomResponseSchema");
    expect(ts).toContain("meetingResponseSchema");
    expect(ts).toContain("problemDetailSchema");
  });

  it("shared api index.ts should export apiClient", () => {
    const ts = readSrc("lib/shared/api/index.ts");
    expect(ts).toContain("apiClient");
  });
});

describe("API Client Guard: de-duplication", () => {
  it("rooms.service.ts should not contain readProblemDetails", () => {
    const ts = readSrc("lib/domains/rooms/rooms.service.ts");
    expect(ts).not.toContain("readProblemDetails");
  });

  it("meetings.service.ts should not duplicate baseHeaders", () => {
    const ts = readSrc("lib/domains/meetings/meetings.service.ts");
    expect(ts).not.toContain("function baseHeaders");
  });
});
