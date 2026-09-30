import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { join, relative } from "node:path";
import ts from "typescript";

const SRC_DIR = join(__dirname, "..");
const PROJECT_DIR = join(SRC_DIR, "..");
const config = ts.readConfigFile(
  join(PROJECT_DIR, "tsconfig.json"),
  ts.sys.readFile,
);
if (config.error)
  throw new Error(
    ts.flattenDiagnosticMessageText(config.error.messageText, "\n"),
  );
const { options, fileNames, errors } = ts.parseJsonConfigFileContent(
  config.config,
  ts.sys,
  PROJECT_DIR,
);
if (errors.length)
  throw new Error(
    ts.formatDiagnosticsWithColorAndContext(
      errors,
      ts.createCompilerHost(options),
    ),
  );

function sourcePath(file: string): string {
  return relative(SRC_DIR, file).replaceAll("\\", "/");
}

function extractImportSpecifiers(content: string, file = "source.ts"): string[] {
  const specifiers: string[] = [];
  const source = ts.createSourceFile(
    file,
    content,
    ts.ScriptTarget.Latest,
    true,
  );
  function visit(node: ts.Node) {
    if (
      (ts.isImportDeclaration(node) || ts.isExportDeclaration(node)) &&
      node.moduleSpecifier &&
      ts.isStringLiteralLike(node.moduleSpecifier)
    ) {
      specifiers.push(node.moduleSpecifier.text);
    } else if (
      ts.isCallExpression(node) &&
      node.expression.kind === ts.SyntaxKind.ImportKeyword &&
      node.arguments[0] &&
      ts.isStringLiteralLike(node.arguments[0])
    ) {
      specifiers.push(node.arguments[0].text);
    } else if (
      ts.isImportTypeNode(node) &&
      ts.isLiteralTypeNode(node.argument) &&
      ts.isStringLiteralLike(node.argument.literal)
    ) {
      specifiers.push(node.argument.literal.text);
    }
    ts.forEachChild(node, visit);
  }
  visit(source);
  return specifiers;
}

// Domains consume these shared contracts, never their implementation files.
const SHARED_PUBLIC_APIS = new Set([
  "lib/shared/index.ts",
  "lib/shared/api/index.ts",
  "lib/shared/security/index.ts",
  "lib/shared/routes/server-handlers.ts",
]);

function importViolations(file: string, content: string): string[] {
  const from = sourcePath(file);
  const violations: string[] = [];
  for (const specifier of extractImportSpecifiers(content, file)) {
    const resolved = ts.resolveModuleName(
      specifier,
      file,
      options,
      ts.sys,
    ).resolvedModule;
    if (!resolved) {
      if (specifier.startsWith(".") || specifier.startsWith("~/")) {
        violations.push(`${from}: cannot resolve ${specifier}`);
      }
      continue;
    }
    const to = sourcePath(resolved.resolvedFileName);
    let violation = "";
    if (
      from.startsWith("routes/") &&
      to.startsWith("lib/domains/") &&
      !/^lib\/domains\/[^/]+\/index\.ts$/.test(to)
    ) {
      violation = "routes must use domain public API barrels";
    } else if (from.startsWith("lib/domains/")) {
      if (to.startsWith("routes/"))
        violation = "domains must not import routes";
      if (to.startsWith("lib/shared/") && !SHARED_PUBLIC_APIS.has(to)) {
        violation = "domains must use shared public APIs";
      }
    } else if (
      from.startsWith("lib/shared/") &&
      (to.startsWith("lib/domains/") || to.startsWith("routes/"))
    ) {
      violation = "shared must not import domains or routes";
    }
    if (violation) violations.push(`${from} -> ${to}: ${violation}`);
  }
  return violations;
}

describe("Import scanning", () => {
  it.each([
    'import {\n  fetchMeetings,\n} from "~/lib/domains/meetings";',
    'export { fetchMeetings } from "~/lib/domains/meetings";',
    'const meetings = import("~/lib/domains/meetings");',
    'type Meeting = import("~/lib/domains/meetings").Meeting;',
    'import "~/lib/domains/meetings";',
  ])("detects a dependency in %s", (content) => {
    expect(extractImportSpecifiers(content)).toEqual([
      "~/lib/domains/meetings",
    ]);
  });
});

describe("Layer Import Guard", () => {
  it.each([
    [
      "routes/meetings/fixture.ts",
      'export const identity = <T>(value: T) => value;\nexport const loadMeetings = () => import("~/lib/domains/meetings/meetings.service");',
    ],
    [
      "routes/meetings/fixture.ts",
      'import { fetchMeetings } from "~/lib/domains/meetings/meetings.service";',
    ],
    [
      "routes/meetings/fixture.ts",
      'import {\n fetchMeetings,\n} from "../../lib/domains/meetings/meetings.service";',
    ],
    [
      "lib/domains/meetings/fixture.ts",
      'export { useMeetings } from "../../../routes/meetings/loaders";',
    ],
    [
      "lib/domains/meetings/fixture.ts",
      'import { createApiClient } from "../../shared/api/client";',
    ],
    [
      "lib/shared/fixture.ts",
      'const meetings = import("../domains/meetings");',
    ],
    [
      "lib/shared/fixture.ts",
      'type Loader = import("../../routes/meetings/loaders");',
    ],
  ])("rejects forbidden dependencies from %s in %s", (file, content) => {
    const violations = importViolations(join(SRC_DIR, file), content);
    expect(violations).toHaveLength(1);
    expect(violations[0]).not.toContain("cannot resolve");
  });

  it.each([
    [
      "routes/meetings/fixture.ts",
      'import { fetchMeetings } from "~/lib/domains/meetings";',
    ],
    [
      "routes/meetings/fixture.ts",
      'export { fetchMeetings } from "../../lib/domains/meetings/index";',
    ],
    [
      "lib/domains/meetings/fixture.ts",
      'import { formatDateTime } from "../../shared";',
    ],
    [
      "lib/domains/meetings/fixture.ts",
      'import { createApiClient } from "~/lib/shared/api";',
    ],
    [
      "lib/domains/meetings/fixture.ts",
      'import { hasPlatformAdminAccess } from "../../shared/security";',
    ],
    [
      "lib/domains/meetings/fixture.ts",
      'import type { ServerRequestContext } from "../../shared/routes/server-handlers";',
    ],
  ])("allows public contracts from %s in %s", (file, content) => {
    expect(importViolations(join(SRC_DIR, file), content)).toEqual([]);
  });

  it.each(["routes/", "lib/domains/", "lib/shared/"])(
    "enforces resolved dependencies in %s",
    (layer) => {
      const files = fileNames.filter((file) =>
        sourcePath(file).startsWith(layer),
      );
      expect(files.length).toBeGreaterThan(0);
      expect(
        files.flatMap((file) =>
          importViolations(file, readFileSync(file, "utf-8")),
        ),
      ).toEqual([]);
    },
  );
});
