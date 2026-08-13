import { createCipheriv, randomBytes } from "node:crypto";
import { createInterface } from "node:readline";
import { pathToFileURL } from "node:url";

const KEY_LENGTHS = new Set([16, 24, 32]);

export function parseConfigSetKey(rawKey) {
  const normalized = rawKey.trim();
  let key;
  if (
    normalized.length % 4 === 0
    && /^[A-Za-z0-9+/]*={0,2}$/.test(normalized)
  ) {
    key = Buffer.from(normalized, "base64");
  } else {
    key = Buffer.from(normalized, "utf8");
  }
  if (!KEY_LENGTHS.has(key.length)) {
    throw new Error("Config-set encryption key must resolve to 16, 24, or 32 bytes.");
  }
  return key;
}

export function encryptConfigSetSecret(rawKey, signingSecret, iv = randomBytes(12)) {
  if (iv.length !== 12) {
    throw new Error("AES-GCM IV must be 12 bytes.");
  }
  if (!signingSecret) {
    throw new Error("Signing secret is required.");
  }
  const key = parseConfigSetKey(rawKey);
  const cipher = createCipheriv(`aes-${key.length * 8}-gcm`, key, iv);
  const ciphertext = Buffer.concat([
    cipher.update(signingSecret, "utf8"),
    cipher.final(),
    cipher.getAuthTag(),
  ]);
  return Buffer.concat([iv, ciphertext]).toString("base64");
}

async function main() {
  const input = [];
  const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
  for await (const line of lines) {
    input.push(line);
  }
  if (input.length !== 2) {
    throw new Error("Expected exactly two stdin lines: encryption key and signing secret.");
  }
  process.stdout.write(`${encryptConfigSetSecret(input[0], input[1])}\n`);
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((error) => {
    process.stderr.write(`encrypt-config-set-secret: ${error.message}\n`);
    process.exitCode = 1;
  });
}
