import { describe, it, expect } from "vitest";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const SRC_DIR = join(__dirname, "..");
const PROJECT_DIR = join(SRC_DIR, "..");

/**
 * Recursively collect all files matching extensions under a directory.
 */
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

function readSrc(filePath: string): string {
  return readFileSync(filePath, "utf-8");
}

function readProject(relativePath: string): string {
  return readFileSync(join(PROJECT_DIR, relativePath), "utf-8");
}

describe("Migration Guard: Qwik patterns MUST be present", () => {
  it("should contain component$ usage in at least one .tsx file", () => {
    const tsxFiles = collectFiles(SRC_DIR, [".tsx"]);
    const hasComponentDollar = tsxFiles.some((f) =>
      readSrc(f).includes("component$"),
    );
    expect(hasComponentDollar).toBe(true);
  });

  it("should contain routeLoader$ or routeAction$ in routes/ files", () => {
    const routesDir = join(SRC_DIR, "routes");
    const routeFiles = collectFiles(routesDir, [".tsx", ".ts"]);
    const hasRouteLoaderOrAction = routeFiles.some((f) => {
      const content = readSrc(f);
      return (
        content.includes("routeLoader$") || content.includes("routeAction$")
      );
    });
    expect(hasRouteLoaderOrAction).toBe(true);
  });

  it("should contain server$ usage in at least one source file", () => {
    const allFiles = collectFiles(SRC_DIR, [".tsx", ".ts"]);
    const sourceFiles = allFiles.filter((f) => !f.endsWith(".test.ts"));
    const hasServerDollar = sourceFiles.some((f) =>
      readSrc(f).includes("server$"),
    );
    expect(hasServerDollar).toBe(true);
  });
});

describe("Story 14.1 Guard: framework and infrastructure baseline", () => {
  it("should NOT use legacy @builder.io import paths in source files", () => {
    const allFiles = collectFiles(SRC_DIR, [".tsx", ".ts"]);
    const sourceFiles = allFiles.filter((f) => !f.endsWith(".test.ts"));

    for (const f of sourceFiles) {
      const content = readSrc(f);
      expect(
        content.includes("@builder.io/qwik"),
        `File ${relative(SRC_DIR, f)} should not use @builder.io/qwik imports`,
      ).toBe(false);
      expect(
        content.includes("@builder.io/qwik-city"),
        `File ${relative(SRC_DIR, f)} should not use @builder.io/qwik-city imports`,
      ).toBe(false);
    }
  });

  it("demo loader route should import domain logic via barrel API", () => {
    const loaderRoute = readProject("src/routes/demo/loader/index.tsx");
    expect(loaderRoute).toContain('from "~/lib/domains/example"');
    expect(loaderRoute).not.toContain("greet-user");
  });

  it("tsconfig should keep strict mode enabled", () => {
    const tsconfig = readProject("tsconfig.json");
    expect(tsconfig).toContain('"strict": true');
  });

  it("Express SSR entry should safely normalize Qwik router export shape", () => {
    const serverEntry = readProject("server/entry.express.mjs");
    expect(serverEntry).toContain(
      'typeof qwikRouter === "function" ? qwikRouter : qwikRouter?.router',
    );
    expect(serverEntry).toContain(
      'typeof qwikRouter === "object" ? qwikRouter?.notFound : undefined',
    );
    expect(serverEntry).toContain('typeof notFoundMiddleware === "function"');
    expect(serverEntry).toContain("Invalid Qwik router middleware export");
  });
});

describe("Migration Guard: Legacy Svelte/SvelteKit patterns MUST be absent", () => {
  it("should NOT contain any .svelte files in src/", () => {
    const svelteFiles = collectFiles(SRC_DIR, [".svelte"]);
    expect(svelteFiles).toEqual([]);
  });

  it("should NOT contain SvelteKit imports in any source file", () => {
    const allFiles = collectFiles(SRC_DIR, [".tsx", ".ts"]);
    const sourceFiles = allFiles.filter((f) => !f.endsWith(".test.ts"));
    const svelteKitImports = [
      "@sveltejs/kit",
      "$app/",
      "svelte/store",
      "svelte/transition",
      "from 'svelte'",
      'from "svelte"',
    ];
    for (const f of sourceFiles) {
      const content = readSrc(f);
      for (const pattern of svelteKitImports) {
        expect(
          content.includes(pattern),
          `File ${relative(SRC_DIR, f)} should not contain "${pattern}"`,
        ).toBe(false);
      }
    }
  });
});

describe("Architecture Guard: routeLoader$/routeAction$ only in routes/", () => {
  it("should NOT export routeLoader$ or routeAction$ outside of routes/", () => {
    const allFiles = collectFiles(SRC_DIR, [".tsx", ".ts"]);
    const routesDir = join(SRC_DIR, "routes");
    const nonRouteFiles = allFiles.filter(
      (f) => !f.startsWith(routesDir) && !f.endsWith(".test.ts"),
    );
    for (const f of nonRouteFiles) {
      const content = readSrc(f);
      expect(
        content.includes("routeLoader$"),
        `File ${relative(SRC_DIR, f)} should not contain routeLoader$ (only allowed in routes/)`,
      ).toBe(false);
      expect(
        content.includes("routeAction$"),
        `File ${relative(SRC_DIR, f)} should not contain routeAction$ (only allowed in routes/)`,
      ).toBe(false);
    }
  });

  it("routes should avoid deep imports from domain internals", () => {
    const routesDir = join(SRC_DIR, "routes");
    const routeFiles = collectFiles(routesDir, [".tsx", ".ts"]);

    for (const f of routeFiles) {
      const content = readSrc(f);
      const importLines = content
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line.startsWith("import ") && line.includes(" from "));

      const hasDeepDomainImport = importLines.some((line) => {
        const match = line.match(/from\s+["']([^"']+)["']/);
        const specifier = match?.[1] ?? "";

        if (!specifier.startsWith("~/lib/domains/")) {
          return false;
        }

        const domainPath = specifier.slice("~/lib/domains/".length);
        return domainPath.includes("/");
      });

      expect(
        hasDeepDomainImport,
        `File ${relative(SRC_DIR, f)} should import domains only via ~/lib/domains/<domain>`,
      ).toBe(false);
    }
  });
});
