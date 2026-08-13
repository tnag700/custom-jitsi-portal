import { describe, it, expect } from "vitest";
import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";

const SRC_DIR = join(__dirname, "..");
const PROJECT_DIR = join(SRC_DIR, "..");
const REPOSITORY_DIR = join(PROJECT_DIR, "..");

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

  it("should keep server$ example outside of production route tree", () => {
    const serverExample = readProject(
      "src/dev-examples/server-function-example.tsx",
    );
    expect(serverExample).toContain("server$");
  });
});

describe("Story 14.1 Guard: framework and infrastructure baseline", () => {
  it("pins one supported Node LTS runtime across development and containers", () => {
    const packageJson = readProject("package.json");
    const serverPackageJson = readProject("server/package.json");
    const dockerfile = readProject("Dockerfile");
    const nvmrc = readFileSync(join(REPOSITORY_DIR, ".nvmrc"), "utf-8").trim();
    const devMonitoring = readFileSync(
      join(REPOSITORY_DIR, "docker-compose.monitoring.yml"),
      "utf-8",
    );
    const productionMonitoring = readFileSync(
      join(REPOSITORY_DIR, "docker-compose.production.monitoring.yml"),
      "utf-8",
    );

    expect(nvmrc).toBe("24.18.0");
    expect(packageJson).toContain('"node": ">=24.18.0 <25"');
    expect(serverPackageJson).toContain('"node": ">=24.18.0 <25"');
    expect(dockerfile.match(/FROM node:24\.18\.0-alpine/g)).toHaveLength(2);
    expect(devMonitoring).toContain("image: node:24.18.0-alpine");
    expect(productionMonitoring).toContain("image: node:24.18.0-alpine");
  });

  it("pins one supported Keycloak patch across development and production", () => {
    const devCompose = readFileSync(
      join(REPOSITORY_DIR, "docker-compose.yml"),
      "utf-8",
    );
    const productionCompose = readFileSync(
      join(REPOSITORY_DIR, "docker-compose.production.yml"),
      "utf-8",
    );
    const expectedImage = "image: quay.io/keycloak/keycloak:26.7.0";

    expect(devCompose.split(expectedImage)).toHaveLength(2);
    expect(productionCompose).toContain("image: jitsi-keycloak:26.7.0");
    expect(productionCompose).toContain("context: ./deploy/keycloak");
    expect(devCompose).not.toContain("keycloak:26.1.2");
    expect(productionCompose).not.toContain("keycloak:26.1.2");
  });

  it("pins one supported Jitsi release across all conference services", () => {
    const productionImages = {
      web: "ghcr.io/jitsi/web:stable-11146-1@sha256:ff81559621732d3dfc4815f261d41fd826566833016ea772f4d43a77aa88fe9a",
      prosody:
        "ghcr.io/jitsi/prosody:stable-11146-1@sha256:0e3d9ada40c03e6eef151348e0872dce7b4b1c16c173ff4a67afeae60aba2404",
      jicofo:
        "ghcr.io/jitsi/jicofo:stable-11146-1@sha256:a5da296923010dcc2daf6a02e6a183181906cb969a088ae90b97516bdeb9737f",
      jvb: "ghcr.io/jitsi/jvb:stable-11146-1@sha256:6a7cec66c6a2fdd8ffd3a90101a0f8e3297aff29494f258caf1bcfbd418a17f3",
    } as const;
    const services = Object.keys(productionImages) as Array<
      keyof typeof productionImages
    >;

    const devSource = readFileSync(
      join(REPOSITORY_DIR, "docker-compose.yml"),
      "utf-8",
    );
    const productionSource = readFileSync(
      join(REPOSITORY_DIR, "docker-compose.production.yml"),
      "utf-8",
    );
    for (const service of services) {
      expect(devSource).toContain(`image: jitsi/${service}:stable-10978`);
      expect(productionSource).toContain(`image: ${productionImages[service]}`);
    }
    expect(
      productionSource.match(
        /image: ghcr\.io\/jitsi\/(?:web|prosody|jicofo|jvb):/g,
      ),
    ).toHaveLength(services.length);
    expect(productionSource).not.toContain("stable-10741");
  });

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

  it("demo routes should not exist in src/routes production tree", () => {
    expect(existsSync(join(SRC_DIR, "routes", "demo"))).toBe(false);
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

  it("Qwik routes should define CSP via route plugin and @nonce", () => {
    const plugin = readProject("src/routes/plugin@csp.ts");
    expect(plugin).toContain('sharedMap.set("@nonce", nonce)');
    expect(plugin).toContain('"Content-Security-Policy"');
    expect(plugin).toContain("buildDocumentContentSecurityPolicy");
    expect(plugin).toContain("shouldApplyDocumentSecurityHeaders");
  });

  it("provides one reproducible repository gate without duplicate OpenAPI generation", () => {
    const repositoryPackage = JSON.parse(
      readFileSync(join(REPOSITORY_DIR, "package.json"), "utf-8"),
    ) as { scripts: Record<string, string> };
    const contractGuard = readFileSync(
      join(REPOSITORY_DIR, "scripts", "check-contracts.mjs"),
      "utf-8",
    );
    const repositoryGate = readFileSync(
      join(REPOSITORY_DIR, "scripts", "verify-repository.mjs"),
      "utf-8",
    );

    expect(repositoryPackage.scripts.verify).toBe(
      "node scripts/verify-repository.mjs",
    );
    expect(repositoryPackage.scripts["contracts:check"]).toBe(
      "node scripts/check-contracts.mjs",
    );
    expect(contractGuard.match(/generate-openapi\.mjs/g)).toHaveLength(1);
    expect(repositoryGate.match(/check-contracts\.mjs/g)).toHaveLength(1);
    expect(repositoryGate).toContain("--with-backend-gates");
    expect(repositoryGate).not.toContain("gradlew");
    expect(repositoryGate).not.toContain("check-openapi.mjs");
    expect(repositoryGate).not.toContain("check-frontend-api-types.mjs");
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
  it("keeps the cabinet join route split by delivery concern", () => {
    const routesDir = join(SRC_DIR, "routes");
    const expectedModules = [
      "join-loaders.ts",
      "join-action.ts",
      "join-page.tsx",
    ];

    for (const moduleName of expectedModules) {
      expect(existsSync(join(routesDir, moduleName))).toBe(true);
    }

    const indexSource = readSrc(join(routesDir, "index.tsx"));
    expect(indexSource).not.toContain("routeLoader$");
    expect(indexSource).not.toContain("routeAction$");
  });

  it("keeps the profile route split by delivery concern", () => {
    const profileDir = join(SRC_DIR, "routes", "profile");
    const expectedModules = ["loader.ts", "action.ts", "profile-page.tsx"];

    for (const moduleName of expectedModules) {
      expect(existsSync(join(profileDir, moduleName))).toBe(true);
    }

    const indexSource = readSrc(join(profileDir, "index.tsx"));
    expect(indexSource).not.toContain("routeLoader$");
    expect(indexSource).not.toContain("routeAction$");
  });

  it("keeps meeting route orchestration split by use case", () => {
    const meetingsRouteDir = join(SRC_DIR, "routes", "meetings");
    const expectedModules = [
      "loaders.ts",
      "meeting-actions.ts",
      "participant-actions.ts",
      "invite-actions.ts",
    ];

    for (const moduleName of expectedModules) {
      expect(existsSync(join(meetingsRouteDir, moduleName))).toBe(true);
    }
    expect(existsSync(join(meetingsRouteDir, "route-handlers.ts"))).toBe(false);
  });

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
        .filter(
          (line) => line.startsWith("import ") && line.includes(" from "),
        );

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

  it("production route graph should not include demo routes", () => {
    const routeFiles = collectFiles(join(SRC_DIR, "routes"), [".tsx", ".ts"]);
    const demoRouteFiles = routeFiles.filter((f) =>
      relative(join(SRC_DIR, "routes"), f).startsWith("demo"),
    );

    expect(demoRouteFiles).toEqual([]);
  });
});
