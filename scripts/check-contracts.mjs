import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const includeBackendGates = process.argv.includes("--with-backend-gates");
const trackedArtifacts = [
  {
    label: "OpenAPI snapshot",
    path: path.join(repoRoot, "openapi.generated.json"),
  },
  {
    label: "frontend API types",
    path: path.join(
      repoRoot,
      "frontend-qwik",
      "src",
      "lib",
      "shared",
      "api",
      "generated",
      "api-types.ts",
    ),
  },
];

for (const artifact of trackedArtifacts) {
  if (!existsSync(artifact.path)) {
    console.error(
      `${artifact.label} is missing: ${path.relative(repoRoot, artifact.path)}`,
    );
    process.exit(1);
  }
}

const before = new Map(
  trackedArtifacts.map((artifact) => [
    artifact.path,
    readFileSync(artifact.path),
  ]),
);

function runNodeScript(scriptName, args = []) {
  const result = spawnSync(
    process.execPath,
    [path.join(scriptDir, scriptName), ...args],
    {
      cwd: repoRoot,
      stdio: "inherit",
    },
  );

  if (result.error) {
    console.error(`Unable to run ${scriptName}: ${result.error.message}`);
    process.exit(1);
  }
  if (result.status !== 0) {
    process.exit(result.status ?? 1);
  }
}

// Generate the backend contract exactly once, then derive the frontend types
// from that same snapshot.
runNodeScript(
  "generate-openapi.mjs",
  includeBackendGates ? ["test", "validateQuality"] : [],
);
runNodeScript("generate-frontend-api-types.mjs");

const driftedArtifacts = trackedArtifacts.filter(
  (artifact) => !before.get(artifact.path).equals(readFileSync(artifact.path)),
);

if (driftedArtifacts.length > 0) {
  console.error("Generated contract drift detected:");
  for (const artifact of driftedArtifacts) {
    console.error(
      `- ${artifact.label}: ${path.relative(repoRoot, artifact.path)}`,
    );
  }
  console.error(
    "Review and commit both generated artifacts as one contract change.",
  );
  process.exit(1);
}

console.log("OpenAPI snapshot and frontend API types are in sync.");
