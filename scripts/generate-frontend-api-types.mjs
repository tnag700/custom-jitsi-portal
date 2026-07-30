import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const frontendDir = path.join(repoRoot, "frontend-qwik");
const cliPath = path.join(
  frontendDir,
  "node_modules",
  "openapi-typescript",
  "bin",
  "cli.js",
);
const openApiPath = path.join(repoRoot, "openapi.generated.json");
const generatedTypesPath = path.join(
  frontendDir,
  "src",
  "lib",
  "shared",
  "api",
  "generated",
  "api-types.ts",
);

if (!existsSync(cliPath)) {
  console.error(
    "openapi-typescript is not installed. Run npm ci in frontend-qwik first.",
  );
  process.exit(1);
}

if (!existsSync(openApiPath)) {
  console.error(
    "openapi.generated.json is missing. Run npm run openapi:generate first.",
  );
  process.exit(1);
}

const result = spawnSync(
  process.execPath,
  [cliPath, openApiPath, "-o", generatedTypesPath],
  {
    cwd: frontendDir,
    stdio: "inherit",
  },
);

if (result.error) {
  console.error(
    `Unable to generate frontend API types: ${result.error.message}`,
  );
  process.exit(1);
}

process.exit(result.status ?? 1);
