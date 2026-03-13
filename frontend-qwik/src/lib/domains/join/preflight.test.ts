import { describe, expect, it } from "vitest";
import {
  createInitialPreflightReport,
  createPreflightJoinError,
  mergePreflightReport,
  resolveRetryPreflightScope,
  type JoinPreflightReport,
} from "./preflight";
import type { JoinReadinessPayload } from "./types";

function createSnapshot(): JoinReadinessPayload {
  return {
    status: "ready",
    checkedAt: "2026-03-09T00:00:00.000Z",
    traceId: "trace-1",
    publicJoinUrl: "https://localhost:8443/",
    systemChecks: [
      {
        key: "backend",
        status: "ok",
        headline: "Backend API доступен",
        reason: "ready",
        actions: ["continue"],
        errorCode: null,
        blocking: false,
      },
    ],
  };
}

describe("join preflight", () => {
  it("maps media-related errors to media-only recheck", () => {
    expect(resolveRetryPreflightScope("MEDIA_PERMISSION_DENIED")).toBe("media");
  });

  it("maps network-related errors to system-only recheck", () => {
    expect(resolveRetryPreflightScope("NETWORK_UNREACHABLE")).toBe("system");
  });

  it("creates blocking join error from failing scoped check", () => {
    const report: JoinPreflightReport = {
      status: "blocked",
      checkedAt: "2026-03-09T00:00:00.000Z",
      traceId: "trace-2",
      publicJoinUrl: "https://localhost:8443/",
      systemChecks: [],
      mediaChecks: [
        {
          key: "media-permissions",
          status: "error",
          headline: "Доступ к медиа запрещён",
          reason: "browser denied",
          actions: ["allow access"],
          errorCode: "MEDIA_PERMISSION_DENIED",
          blocking: true,
        },
      ],
    };

    expect(createPreflightJoinError(report, "media")).toEqual({
      title: "Доступ к медиа запрещён",
      detail: "browser denied allow access",
      errorCode: "MEDIA_PERMISSION_DENIED",
      traceId: "trace-2",
    });
  });

  it("merges backend snapshot with browser checks and keeps degraded status", () => {
    const snapshot = createSnapshot();
    const initial = createInitialPreflightReport(snapshot);

    const merged = mergePreflightReport(
      initial,
      snapshot,
      {
        systemChecks: [],
        mediaChecks: [
          {
            key: "media-devices",
            status: "warn",
            headline: "Не всё оборудование обнаружено",
            reason: "camera missing",
            actions: ["check camera"],
            errorCode: "MEDIA_PARTIAL_DEVICE_SET",
            blocking: false,
          },
        ],
      },
      "full",
    );

    expect(merged.status).toBe("degraded");
    expect(merged.systemChecks).toHaveLength(1);
    expect(merged.mediaChecks).toHaveLength(1);
  });
});