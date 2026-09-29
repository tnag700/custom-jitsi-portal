import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

const PROJECT_DIR = join(__dirname, "..", "..");

interface PackageManifest {
  scripts: Record<string, string>;
  dependencies: Record<string, string>;
  devDependencies: Record<string, string>;
  overrides: Record<string, string | Record<string, string>>;
  allowScripts: Record<string, boolean>;
}

interface LockPackage {
  name?: string;
  version?: string;
}

interface PackageLock {
  packages: Record<string, LockPackage>;
}

function readJson<T>(fileName: string): T {
  return JSON.parse(
    readFileSync(join(PROJECT_DIR, fileName), "utf-8"),
  ) as T;
}

describe("frontend toolchain version contract", () => {
  const manifest = readJson<PackageManifest>("package.json");
  const lock = readJson<PackageLock>("package-lock.json");

  it("keeps the Qwik core, router and lint plugin on one compatibility beta", () => {
    const qwikVersion = manifest.devDependencies["@qwik.dev/core"];

    expect(qwikVersion).toBe("2.0.0-beta.45");
    expect(manifest.devDependencies["@qwik.dev/router"]).toBe(qwikVersion);
    expect(manifest.devDependencies["eslint-plugin-qwik"]).toBe(qwikVersion);
    expect(manifest.dependencies["@qwik-ui/headless"]).toBe("0.7.7");
  });

  it("aliases legacy Qwik UI peers to the same v2 runtime", () => {
    expect(manifest.overrides["@builder.io/qwik"]).toBe(
      "npm:@qwik.dev/core@2.0.0-beta.45",
    );
    expect(manifest.overrides["@builder.io/qwik-city"]).toBe(
      "npm:@qwik.dev/router@2.0.0-beta.45",
    );
    expect(lock.packages["node_modules/@builder.io/qwik"]).toMatchObject({
      name: "@qwik.dev/core",
      version: "2.0.0-beta.45",
    });

    const installedQwikVersions = Object.entries(lock.packages)
      .filter(
        ([path, value]) =>
          path.endsWith("node_modules/@qwik.dev/core") ||
          value.name === "@qwik.dev/core",
      )
      .map(([, value]) => value.version);

    expect(new Set(installedQwikVersions)).toEqual(
      new Set(["2.0.0-beta.45"]),
    );
  });

  it("keeps audited transitive build tools on the reviewed patched versions", () => {
    expect(
      lock.packages["node_modules/@redocly/openapi-core"]?.version,
    ).toBe("1.34.20");
    expect(lock.packages["node_modules/js-yaml"]?.version).toBe("4.3.2");
    expect(lock.packages["node_modules/nanoid"]?.version).toBe("3.3.19");
    expect(lock.packages["node_modules/postcss"]?.version).toBe("8.5.28");
    expect(lock.packages["node_modules/minimatch"]?.version).toBe("10.2.6");
    expect(lock.packages["node_modules/uuid"]?.version).toBe("11.1.1");
    expect(lock.packages["node_modules/sharp"]?.version).toBe("0.35.4");
  });

  it("keeps the lint and Node type toolchain on the audited compatible set", () => {
    expect(manifest.devDependencies["@eslint/js"]).toBe("10.0.1");
    expect(manifest.devDependencies.eslint).toBe("10.11.0");
    expect(manifest.devDependencies["typescript-eslint"]).toBe("8.71.0");
    expect(manifest.devDependencies.globals).toBe("17.12.0");
    expect(manifest.devDependencies["@types/node"]).toBe("24.19.0");
  });

  it("uses TypeScript 7 for type checking and keeps the JS API for tooling", () => {
    expect(manifest.devDependencies["@typescript/native"]).toBe("npm:typescript@7.0.2");
    expect(lock.packages["node_modules/@typescript/native"]?.version).toBe("7.0.2");
    expect(manifest.scripts["build.types"]).toBe(
      "node node_modules/@typescript/native/bin/tsc --incremental --noEmit",
    );
    expect(manifest.devDependencies.typescript).toBe("5.9.3");
  });

  it("does not restore unused coverage and CSS-module tooling", () => {
    expect(manifest.devDependencies).not.toHaveProperty("@vitest/coverage-v8");
    expect(manifest.devDependencies).not.toHaveProperty(
      "typescript-plugin-css-modules",
    );
  });

  it("blocks unneeded install scripts", () => {
    expect(manifest.allowScripts).toEqual({
      fsevents: false,
    });
    expect(
      readFileSync(join(PROJECT_DIR, ".npmrc"), "utf-8").trim(),
    ).toBe("strict-allow-scripts=true");
  });
});
