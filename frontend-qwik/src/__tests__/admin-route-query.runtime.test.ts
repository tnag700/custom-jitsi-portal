import { describe, expect, it } from "vitest";
import {
  buildAdminOverviewHref,
  buildAdminQueryHref,
  normalizeNonNegativeAdminInteger,
  normalizePositiveAdminInteger,
  sanitizeAdminQueryValue,
} from "../lib/domains/admin/admin-route-query";

describe("admin route query helpers", () => {
  it("trims query values and normalizes missing input", () => {
    expect(sanitizeAdminQueryValue("  dev  ")).toBe("dev");
    expect(sanitizeAdminQueryValue(null)).toBe("");
  });

  it("builds hrefs while removing empty query params", () => {
    const currentUrl = new URL("https://portal.example.test/admin/incidents?environment=dev&view=critical&traceId=trace-1");

    expect(buildAdminQueryHref(currentUrl, {
      view: "active",
      traceId: null,
      requestId: "req-1",
      category: "",
    })).toBe("/admin/incidents?environment=dev&view=active&requestId=req-1");
  });

  it("normalizes numeric filters to safe bounded values", () => {
    expect(normalizePositiveAdminInteger("25", 50)).toBe(25);
    expect(normalizePositiveAdminInteger("0", 50)).toBe(50);
    expect(normalizePositiveAdminInteger("oops", 50)).toBe(50);
    expect(normalizeNonNegativeAdminInteger("12", 0)).toBe(12);
    expect(normalizeNonNegativeAdminInteger("-4", 0)).toBe(0);
    expect(normalizeNonNegativeAdminInteger("oops", 0)).toBe(0);
  });

  it("builds overview links with optional environment context", () => {
    expect(buildAdminOverviewHref("")).toBe("/admin");
    expect(buildAdminOverviewHref("prod")).toBe("/admin?environment=prod");
    expect(buildAdminOverviewHref("prod blue")).toBe("/admin?environment=prod%20blue");
  });
});