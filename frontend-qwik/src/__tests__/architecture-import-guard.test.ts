import { describe, expect, it } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const SRC_DIR = join(__dirname, "..");

function collectFiles(dir: string, extensions: string[]): string[] {
  const results: string[] = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (entry === "node_modules" || entry === ".git") continue;

    if (statSync(full).isDirectory()) {
      results.push(...collectFiles(full, extensions));
    } else if (extensions.some((ext) => full.endsWith(ext))) {
      results.push(full);
    }
  }
  return results;
}

function extractImportSpecifiers(content: string): string[] {
  return content
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.startsWith("import ") && line.includes(" from "))
    .map((line) => line.match(/from\s+["']([^"']+)["']/)?.[1] ?? "")
    .filter((specifier) => specifier.length > 0);
}

describe("Layer Import Guard", () => {
  it("routes import domains only via public API barrel", () => {
    const routeFiles = collectFiles(join(SRC_DIR, "routes"), [".ts", ".tsx"]);

    for (const file of routeFiles) {
      const content = readFileSync(file, "utf-8");
      const specifiers = extractImportSpecifiers(content);

      const hasDeepDomainImport = specifiers.some((specifier) => {
        if (!specifier.startsWith("~/lib/domains/")) return false;
        const domainPath = specifier.slice("~/lib/domains/".length);
        return domainPath.includes("/");
      });

      expect(
        hasDeepDomainImport,
        `Route ${relative(SRC_DIR, file)} should import domain only as ~/lib/domains/<domain>`,
      ).toBe(false);
    }
  });

  it("domains do not import route layer and use shared barrel only", () => {
    const domainFiles = collectFiles(join(SRC_DIR, "lib", "domains"), [".ts", ".tsx"]);

    for (const file of domainFiles) {
      const content = readFileSync(file, "utf-8");
      const specifiers = extractImportSpecifiers(content);

      const importsRouteLayer = specifiers.some((specifier) =>
        specifier.startsWith("~/routes/"),
      );

      const hasDeepSharedImport = specifiers.some((specifier) => {
        if (!specifier.startsWith("~/lib/shared/")) return false;
        return specifier !== "~/lib/shared";
      });

      expect(
        importsRouteLayer,
        `Domain file ${relative(SRC_DIR, file)} must not import route layer`,
      ).toBe(false);
      expect(
        hasDeepSharedImport,
        `Domain file ${relative(SRC_DIR, file)} must import shared only via ~/lib/shared`,
      ).toBe(false);
    }
  });

  it("shared layer does not import domains or routes", () => {
    const sharedFiles = collectFiles(join(SRC_DIR, "lib", "shared"), [".ts", ".tsx"]);

    for (const file of sharedFiles) {
      const content = readFileSync(file, "utf-8");
      const specifiers = extractImportSpecifiers(content);

      const importsDomains = specifiers.some((specifier) =>
        specifier.startsWith("~/lib/domains/"),
      );
      const importsRoutes = specifiers.some((specifier) =>
        specifier.startsWith("~/routes/"),
      );

      expect(
        importsDomains,
        `Shared file ${relative(SRC_DIR, file)} must not import domain layer`,
      ).toBe(false);
      expect(
        importsRoutes,
        `Shared file ${relative(SRC_DIR, file)} must not import route layer`,
      ).toBe(false);
    }
  });
});
