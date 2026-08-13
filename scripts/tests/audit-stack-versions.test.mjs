import assert from "node:assert/strict";
import test from "node:test";
import { auditStackVersions, resolveLatest } from "../audit-stack-versions.mjs";

test("offline audit fails closed on drift and reports the reviewed baseline", async () => {
  const result = await auditStackVersions({ online: false });
  assert.equal(result.online, false);
  assert.ok(result.components.length >= 8);
  assert.deepEqual(
    result.components.filter((component) => component.updateAvailable),
    [],
  );
  assert.equal(
    result.components.find((component) => component.key === "spring-boot")?.currentVersion,
    "4.1.0",
  );
  assert.equal(
    result.components.find((component) => component.key === "qwik")?.channel,
    "beta",
  );
});

test("release lookup uses the configured prerelease channel document", async () => {
  const requested = [];
  const latest = await resolveLatest(
    {
      key: "qwik",
      channel: "beta",
      sourceUrl: "https://registry.npmjs.org/@qwik.dev%2fcore/beta",
    },
    async (url, options) => {
      requested.push({ url: String(url), redirect: options.redirect });
      return new Response(JSON.stringify({ version: "2.0.0-beta.39" }), {
        status: 200,
      });
    },
  );
  assert.equal(latest, "2.0.0-beta.39");
  assert.deepEqual(requested, [
    {
      url: "https://registry.npmjs.org/@qwik.dev%2fcore/beta",
      redirect: "error",
    },
  ]);
});

test("release lookup rejects non-allowlisted hosts before network access", async () => {
  await assert.rejects(
    resolveLatest(
      { key: "bad", channel: "latest", sourceUrl: "https://example.org/latest" },
      async () => assert.fail("network must not be called"),
    ),
    /non-allowlisted release source/,
  );
});
