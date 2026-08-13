import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(scriptDir, "..");
const baselinePath = path.join(scriptDir, "stack-version-baseline.json");
const CHECK_TIMEOUT_MS = 10_000;
const MAX_RESPONSE_BYTES = 1_048_576;

function fail(message) {
  throw new Error(`stack-version-audit: ${message}`);
}

function parseVersion(value, label) {
  const normalized = String(value ?? "").trim();
  if (!/^[0-9]+(?:\.[0-9]+){1,3}(?:-[0-9A-Za-z.-]+)?$/.test(normalized)) {
    fail(`${label} contains an invalid version '${normalized}'.`);
  }
  return normalized;
}

function stableVersions(versions) {
  return versions.filter((version) => /^\d+(?:\.\d+){1,3}$/.test(version));
}

function compareVersions(left, right) {
  const a = left.split(".").map(Number);
  const b = right.split(".").map(Number);
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index += 1) {
    const difference = (a[index] ?? 0) - (b[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

async function fetchBoundedText(url, fetchImpl) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), CHECK_TIMEOUT_MS);
  try {
    const response = await fetchImpl(url, {
      redirect: "error",
      signal: controller.signal,
      headers: { Accept: "application/json, application/xml, text/xml" },
    });
    if (!response.ok) fail(`${url} returned HTTP ${response.status}.`);
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.byteLength > MAX_RESPONSE_BYTES) fail(`${url} response is too large.`);
    return new TextDecoder().decode(bytes);
  } finally {
    clearTimeout(timeout);
  }
}

export async function resolveLatest(component, fetchImpl = globalThis.fetch) {
  if (component.channel === "spring-boot-managed") {
    return component.latestVersion;
  }
  const url = new URL(component.sourceUrl);
  const allowedHosts = new Set([
    "repo.maven.apache.org",
    "services.gradle.org",
    "registry.npmjs.org",
  ]);
  if (url.protocol !== "https:" || !allowedHosts.has(url.hostname)) {
    fail(`${component.key} uses a non-allowlisted release source.`);
  }

  const body = await fetchBoundedText(url, fetchImpl);
  if (url.hostname === "services.gradle.org") {
    const document = JSON.parse(body);
    if (document.snapshot || document.nightly || document.rcFor || document.milestoneFor) {
      fail("Gradle current endpoint returned a non-GA release.");
    }
    return parseVersion(document.version, component.key);
  }
  if (url.hostname === "registry.npmjs.org") {
    const document = JSON.parse(body);
    return parseVersion(document.version, component.key);
  }

  const versions = stableVersions(
    [...body.matchAll(/<version>([^<]+)<\/version>/g)].map((match) => match[1].trim()),
  );
  if (versions.length === 0) fail(`${component.key} metadata has no stable versions.`);
  return versions.sort(compareVersions).at(-1);
}

function extractLocalVersions(buildGradle, wrapperProperties, packageLock) {
  const match = (pattern, label, source) => {
    const result = source.match(pattern);
    if (!result) fail(`cannot resolve local ${label} version.`);
    return parseVersion(result[1], label);
  };
  const packages = packageLock.packages ?? {};
  return new Map([
    ["spring-boot", match(/org\.springframework\.boot' version '([^']+)'/, "Spring Boot", buildGradle)],
    ["spring-modulith", match(/spring-modulith-bom:([^']+)'/, "Spring Modulith", buildGradle)],
    ["spring-retry", match(/spring-retry:([^']+)'/, "Spring Retry", buildGradle)],
    ["springdoc", match(/springdoc-openapi-starter-webmvc-api:([^']+)'/, "springdoc-openapi", buildGradle)],
    ["gradle", match(/gradle-([0-9.]+)-bin\.zip/, "Gradle", wrapperProperties)],
    ["qwik", parseVersion(packages["node_modules/@qwik.dev/core"]?.version, "Qwik")],
    ["qwik-router", parseVersion(packages["node_modules/@qwik.dev/router"]?.version, "Qwik Router")],
    ["express", parseVersion(packages["node_modules/express"]?.version, "Express")],
  ]);
}

export async function auditStackVersions({ online = true, fetchImpl = globalThis.fetch } = {}) {
  const [baselineText, buildGradle, wrapperProperties, packageLockText] = await Promise.all([
    fs.readFile(baselinePath, "utf8"),
    fs.readFile(path.join(repoRoot, "backend", "build.gradle"), "utf8"),
    fs.readFile(path.join(repoRoot, "backend", "gradle", "wrapper", "gradle-wrapper.properties"), "utf8"),
    fs.readFile(path.join(repoRoot, "frontend-qwik", "package-lock.json"), "utf8"),
  ]);
  const baseline = JSON.parse(baselineText);
  const localVersions = extractLocalVersions(buildGradle, wrapperProperties, JSON.parse(packageLockText));
  const results = [];

  for (const component of baseline.components) {
    const currentVersion = localVersions.get(component.key) ?? parseVersion(component.currentVersion, component.key);
    if (currentVersion !== component.currentVersion) {
      fail(`${component.displayName} local version ${currentVersion} differs from baseline ${component.currentVersion}.`);
    }
    const latestVersion = online ? await resolveLatest(component, fetchImpl) : component.latestVersion;
    results.push({ ...component, currentVersion, latestVersion, updateAvailable: currentVersion !== latestVersion });
  }
  return { checkedAt: baseline.checkedAt, online, components: results };
}

const isEntrypoint = process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isEntrypoint) {
  const online = !process.argv.includes("--offline");
  auditStackVersions({ online })
    .then((result) => {
      const updates = result.components.filter((component) => component.updateAvailable);
      for (const component of result.components) {
        const marker = component.updateAvailable ? "UPDATE" : "OK";
        process.stdout.write(`${marker}\t${component.displayName}\t${component.currentVersion}\t${component.latestVersion}\n`);
      }
      if (updates.length > 0) {
        process.stderr.write(`stack-version-audit: ${updates.length} update(s) available.\n`);
        process.exitCode = 2;
      } else {
        process.stdout.write("stack-version-audit: all monitored release channels are current.\n");
      }
    })
    .catch((error) => {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    });
}
