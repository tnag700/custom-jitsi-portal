import assert from "node:assert/strict";
import { createDecipheriv } from "node:crypto";
import test from "node:test";
import {
  encryptConfigSetSecret,
  parseConfigSetKey,
} from "../encrypt-config-set-secret.mjs";

test("matches the Java AES-GCM payload layout", () => {
  const rawKey = "0123456789ABCDEF0123456789ABCDEF";
  const signingSecret = "production-signing-secret-at-least-32-bytes";
  const iv = Buffer.from("000102030405060708090a0b", "hex");
  const encoded = encryptConfigSetSecret(rawKey, signingSecret, iv);
  const payload = Buffer.from(encoded, "base64");
  const key = parseConfigSetKey(rawKey);
  const ciphertextWithTag = payload.subarray(12);
  const decipher = createDecipheriv(`aes-${key.length * 8}-gcm`, key, payload.subarray(0, 12));
  decipher.setAuthTag(ciphertextWithTag.subarray(ciphertextWithTag.length - 16));
  const plaintext = Buffer.concat([
    decipher.update(ciphertextWithTag.subarray(0, ciphertextWithTag.length - 16)),
    decipher.final(),
  ]).toString("utf8");
  assert.equal(plaintext, signingSecret);
});

test("rejects invalid effective AES key lengths", () => {
  assert.throws(() => parseConfigSetKey("short"), /16, 24, or 32 bytes/);
});
