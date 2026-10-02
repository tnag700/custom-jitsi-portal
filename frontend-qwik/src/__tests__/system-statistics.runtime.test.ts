import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchSystemStatistics } from "../lib/domains/statistics/statistics.service";
import { startVisiblePolling } from "../lib/shared/api/visible-polling";

const summary = { backendState: "working", cpuPercent: 65, memoryPercent: 40, diskState: "low", measuredAt: "2026-10-02T09:00:00Z", stale: false, monitoringConfigured: true };
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe("safe system statistics", () => {
  it("uses credentials in the browser and rejects detailed fields", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(summary)));
    vi.stubGlobal("fetch", fetch);
    expect(await fetchSystemStatistics("/api/v1")).toEqual(summary);
    expect(fetch.mock.calls[0][1]).toMatchObject({ credentials: "include", cache: "no-store", headers: {} });
    fetch.mockResolvedValue(new Response(JSON.stringify({ ...summary, instance: "internal-host", query: "up" })));
    await expect(fetchSystemStatistics("/api/v1")).rejects.toThrow();
    fetch.mockResolvedValue(new Response("", { status: 401 }));
    await expect(fetchSystemStatistics("/api/v1")).rejects.toMatchObject({ status: 401 });
  });

  it("polls only visible pages, cancels disposal and stops after session expiry", async () => {
    vi.useFakeTimers();
    const document = Object.assign(new EventTarget(), { visibilityState: "hidden" });
    vi.stubGlobal("document", document);
    const update = vi.fn().mockResolvedValue(true);
    const stop = startVisiblePolling(update);
    await vi.advanceTimersByTimeAsync(120_000);
    expect(update).not.toHaveBeenCalled();
    document.visibilityState = "visible";
    document.dispatchEvent(new Event("visibilitychange"));
    await vi.advanceTimersByTimeAsync(60_000);
    expect(update).toHaveBeenCalledTimes(2);
    update.mockResolvedValue(false);
    await vi.advanceTimersByTimeAsync(60_000);
    const calls = update.mock.calls.length;
    await vi.advanceTimersByTimeAsync(120_000);
    expect(update).toHaveBeenCalledTimes(calls);
    stop();
    expect(update.mock.calls.at(-1)?.[0].aborted).toBe(true);
  });
});
