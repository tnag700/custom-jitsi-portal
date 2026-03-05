import { spawn } from "node:child_process";
import { once } from "node:events";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

const serverEntry = resolve("server/entry.express.mjs");
const distServerEntry = resolve("dist/server/entry.express.js");
const distClientDir = resolve("dist/build");

if (!existsSync(serverEntry) || !existsSync(distServerEntry) || !existsSync(distClientDir)) {
  console.error("Build artifacts are missing. Run `npm run build` first.");
  process.exit(1);
}

const port = Number(process.env.PORT || 4179);
const baseUrl = `http://127.0.0.1:${port}`;

function sleep(ms) {
  return new Promise((resolveSleep) => setTimeout(resolveSleep, ms));
}

async function waitForServerReady(maxAttempts = 50) {
  for (let i = 0; i < maxAttempts; i += 1) {
    try {
      const res = await fetch(`${baseUrl}/`);
      if (res.ok) {
        return;
      }
    } catch {
      // keep polling until the server is ready
    }
    await sleep(100);
  }
  throw new Error(`Server did not become ready on ${baseUrl}`);
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

async function main() {
  const server = spawn(process.execPath, [serverEntry], {
    env: { ...process.env, PORT: String(port) },
    stdio: ["ignore", "pipe", "pipe"],
  });

  let stderr = "";
  server.stderr.on("data", (chunk) => {
    stderr += String(chunk);
  });

  try {
    await waitForServerReady();

    const pageRes = await fetch(`${baseUrl}/auth`);
    assert(pageRes.ok, `Expected /auth to return 200, got ${pageRes.status}`);

    const html = await pageRes.text();
    assert(html.includes("Вход в портал"), "SSR HTML does not include expected auth content");
    assert(html.includes('q:container="paused"') || html.includes("q:container='paused'"), "Resumability marker q:container=paused is missing");
    assert(html.includes("q:base=\"/build/\"") || html.includes("q:base='/build/'"), "Resumability base marker q:base=/build/ is missing");

    const chunkMatch = html.match(/\/build\/q-[A-Za-z0-9_-]+\.js/);
    assert(Boolean(chunkMatch), "No lazy Qwik chunk reference found in SSR HTML");

    const chunkRes = await fetch(`${baseUrl}${chunkMatch[0]}`);
    assert(chunkRes.ok, `Expected JS chunk ${chunkMatch[0]} to return 200, got ${chunkRes.status}`);

    console.log("SSR/resumability smoke check passed");
  } finally {
    server.kill("SIGTERM");
    await Promise.race([once(server, "exit"), sleep(500)]);
    if (stderr.trim()) {
      console.error(stderr.trim());
    }
  }
}

main().catch((error) => {
  console.error(error instanceof Error ? error.message : String(error));
  process.exit(1);
});
