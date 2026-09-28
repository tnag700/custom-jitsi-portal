import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createInitialPreflightReport,
  createPreflightJoinError,
  mergePreflightReport,
  resolveRetryPreflightScope,
  runBrowserPreflight,
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

describe("browser media lifecycle", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function mockBrowser(getUserMedia: () => Promise<MediaStream>) {
    vi.stubGlobal("window", globalThis);
    vi.stubGlobal("navigator", {
      mediaDevices: { getUserMedia, enumerateDevices: async () => [] },
    });
  }

  it("stops every track even when permission arrives after the timeout", async () => {
    vi.useFakeTimers();
    const stopAudio = vi.fn();
    const stopVideo = vi.fn();
    let allow!: (stream: MediaStream) => void;
    mockBrowser(() => new Promise((resolve) => { allow = resolve; }));
    const checking = runBrowserPreflight({ publicJoinUrl: null, scope: "media" });
    await vi.advanceTimersByTimeAsync(3001);
    expect((await checking).mediaChecks).toContainEqual(expect.objectContaining({
      errorCode: "MEDIA_SMOKE_TIMEOUT",
    }));
    allow({ getTracks: () => [{ stop: stopAudio }, { stop: stopVideo }] } as unknown as MediaStream);
    await vi.advanceTimersByTimeAsync(0);
    expect(stopAudio).toHaveBeenCalledOnce();
    expect(stopVideo).toHaveBeenCalledOnce();
  });

  it("preserves asynchronous permission denial as a blocking error", async () => {
    mockBrowser(() => Promise.reject(new DOMException("Denied", "NotAllowedError")));
    const report = await runBrowserPreflight({ publicJoinUrl: null, scope: "media" });
    expect(report.mediaChecks).toContainEqual(expect.objectContaining({
      key: "media-smoke-check", errorCode: "MEDIA_PERMISSION_DENIED", blocking: true,
    }));
  });

  it("reports rejected device enumeration separately from timeout", async () => {
    mockBrowser(async () => ({ getTracks: () => [] }) as unknown as MediaStream);
    vi.spyOn(navigator.mediaDevices, "enumerateDevices").mockRejectedValue(new Error("Unavailable"));
    const report = await runBrowserPreflight({ publicJoinUrl: null, scope: "media" });
    expect(report.mediaChecks).toContainEqual(expect.objectContaining({
      errorCode: "MEDIA_DEVICES_ENUMERATION_FAILED",
    }));
  });
});
