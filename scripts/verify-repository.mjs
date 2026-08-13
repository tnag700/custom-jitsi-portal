import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { rm } from "node:fs/promises";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const npmCli = process.env.npm_execpath;

function run(label, command, args, cwd = repoRoot) {
  console.log(`\n=== ${label} ===`);
  const result = spawnSync(command, args, {
    cwd,
    stdio: "inherit",
    env: process.env,
  });

  if (result.error) {
    console.error(`${label} could not start: ${result.error.message}`);
    process.exit(1);
  }
  if (result.status !== 0) {
    console.error(`${label} failed with exit code ${result.status ?? 1}.`);
    process.exit(result.status ?? 1);
  }
}

function runNpm(label, args) {
  if (npmCli) {
    run(label, process.execPath, [npmCli, ...args]);
    return;
  }

  if (process.platform === "win32") {
    run(label, process.env.ComSpec ?? "cmd.exe", [
      "/d",
      "/s",
      "/c",
      ["npm", ...args].join(" "),
    ]);
    return;
  }

  run(label, "npm", args);
}

run("Generated API contract and backend quality gates", process.execPath, [
  path.join(scriptDir, "check-contracts.mjs"),
  "--with-backend-gates",
]);

runNpm("Frontend tests", ["--prefix", "frontend-qwik", "test"]);
runNpm("Frontend production build", [
  "--prefix",
  "frontend-qwik",
  "run",
  "build",
]);
runNpm("Frontend architecture boundaries", [
  "--prefix",
  "frontend-qwik",
  "run",
  "verify:architecture",
]);
runNpm("Development configuration guardrails", ["run", "stack:validate"]);
runNpm("Reviewed stack version baseline", ["run", "stack:versions:validate"]);
runNpm("Production configuration guardrails", [
  "run",
  "prod:baseline:validate",
]);

const generatedArtifacts = [
  path.join(repoRoot, ".playwright-cli"),
  path.join(repoRoot, "frontend-qwik", "dummy-non-existing-folder"),
];
for (const artifact of generatedArtifacts) {
  if (!existsSync(artifact)) continue;
  await rm(artifact, { recursive: true, force: true });
  if (existsSync(artifact)) {
    console.error(`Generated test artifact could not be cleaned up: ${artifact}`);
    process.exit(1);
  }
}

console.log("\nRepository verification completed successfully.");
