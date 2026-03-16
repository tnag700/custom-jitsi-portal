import { describe, expect, it } from "vitest";
import {
  buildDocumentContentSecurityPolicy,
  createCspNonce,
  resolveDocumentApiUrl,
  shouldApplyDocumentSecurityHeaders,
  STATIC_SECURITY_HEADERS,
} from "~/lib/shared/security/csp";

describe("document security headers", () => {
  it("builds nonce-backed CSP for frontend HTML responses", () => {
    const csp = buildDocumentContentSecurityPolicy({
      nonce: "nonce-123",
      apiUrl: "http://localhost:8080/api/v1",
      publicApiUrl: "http://localhost:8080/api/v1",
    });

    expect(csp).toContain("default-src 'none'");
    expect(csp).toContain("base-uri 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("script-src 'self' 'nonce-nonce-123' 'strict-dynamic'");
    expect(csp).toContain("style-src 'self' 'unsafe-inline'");
    expect(csp).toContain("style-src-attr 'unsafe-inline'");
    expect(csp).toContain("img-src 'self' data: blob:");
    expect(csp).toContain("font-src 'self'");
    expect(csp).toContain("connect-src 'self' http://localhost:8080 https: wss:");
    expect(csp).toContain("manifest-src 'self'");
  });

  it("generates unique nonces and exports browser-hardening headers", () => {
    const first = createCspNonce();
    const second = createCspNonce();

    expect(first).not.toEqual(second);
    expect(first.length).toBeGreaterThan(20);
    expect(STATIC_SECURITY_HEADERS["X-Content-Type-Options"]).toBe("nosniff");
    expect(STATIC_SECURITY_HEADERS["X-Frame-Options"]).toBe("DENY");
    expect(STATIC_SECURITY_HEADERS["Referrer-Policy"]).toBe("no-referrer");
    expect(STATIC_SECURITY_HEADERS["Permissions-Policy"]).toBe(
      "camera=(self), microphone=(self), geolocation=()",
    );
  });

  it("applies document headers only to navigational HTML requests", () => {
    expect(shouldApplyDocumentSecurityHeaders("GET", "/")).toBe(true);
    expect(shouldApplyDocumentSecurityHeaders("HEAD", "/auth")).toBe(true);
    expect(shouldApplyDocumentSecurityHeaders("GET", "/build/q-app.js")).toBe(false);
    expect(shouldApplyDocumentSecurityHeaders("GET", "/assets/app.css")).toBe(false);
    expect(shouldApplyDocumentSecurityHeaders("GET", "/favicon.svg")).toBe(false);
    expect(shouldApplyDocumentSecurityHeaders("POST", "/auth")).toBe(false);
  });

  it("falls back to the local backend origin when API env values are relative", () => {
    const apiUrl = resolveDocumentApiUrl({
      requestUrl: new URL("http://localhost:4173/auth/"),
      apiUrl: "/api/v1",
      publicApiUrl: "/api/v1",
      localDefaultApiUrl: "http://localhost:8080/api/v1",
    });

    expect(apiUrl).toBe("http://localhost:8080/api/v1");
  });

  it("keeps relative API configuration unchanged for non-local hosts", () => {
    const apiUrl = resolveDocumentApiUrl({
      requestUrl: new URL("https://portal.example.com/auth/"),
      apiUrl: "/api/v1",
      publicApiUrl: "/api/v1",
      localDefaultApiUrl: "http://localhost:8080/api/v1",
    });

    expect(apiUrl).toBe("/api/v1");
  });
});