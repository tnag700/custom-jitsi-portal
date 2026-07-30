import { copyFileSync, mkdirSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const backendDir = path.join(repoRoot, "backend");
const generatedFile = path.join(
  backendDir,
  "build",
  "openapi",
  "openapi-generated.json",
);
const committedBaseline = path.join(repoRoot, "openapi.generated.json");
const additionalGradleTasks = process.argv.slice(2);

if (additionalGradleTasks.some((task) => !/^[A-Za-z0-9:_-]+$/.test(task))) {
  console.error("Additional Gradle task names contain unsupported characters.");
  process.exit(1);
}

const result =
  process.platform === "win32"
    ? spawnSync(
        process.env.ComSpec ?? "cmd.exe",
        [
          "/d",
          "/s",
          "/c",
          [
            "gradlew.bat",
            "--no-daemon",
            "--console=plain",
            "generateOpenApiSpec",
            ...additionalGradleTasks,
          ].join(" "),
        ],
        {
          cwd: backendDir,
          stdio: "inherit",
        },
      )
    : spawnSync(
        "./gradlew",
        [
          "--no-daemon",
          "--console=plain",
          "generateOpenApiSpec",
          ...additionalGradleTasks,
        ],
        {
          cwd: backendDir,
          stdio: "inherit",
        },
      );

if (result.status !== 0) {
  process.exit(result.status ?? 1);
}

mkdirSync(path.dirname(committedBaseline), { recursive: true });
copyFileSync(generatedFile, committedBaseline);
