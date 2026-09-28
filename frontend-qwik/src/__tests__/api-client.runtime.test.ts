import { createServer } from "node:http";
import { once } from "node:events";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createApiClient } from "../lib/shared/api/client";
import { fetchJoinReadiness } from "../lib/domains/join/join.service";

afterEach(() => vi.restoreAllMocks());

describe("API request transport", () => {
  it("aborts an in-flight HTTP request at the caller deadline", async () => {
    const server = createServer((_request, response) => {
      setTimeout(() => response.end("[]"), 250);
    });
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    try {
      const address = server.address();
      if (!address || typeof address === "string") throw new Error("No server port");
      const client = createApiClient(`http://127.0.0.1:${address.port}`);
      await expect(client.GET("/api/v1/meetings/upcoming", {
        signal: AbortSignal.timeout(30),
      })).rejects.toMatchObject({ name: "TimeoutError" });
    } finally {
      server.closeAllConnections();
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });

  it.each(["typed", "readiness"])("bounds %s requests even when callers omit a signal", async (kind) => {
    const deadline = AbortSignal.timeout(30);
    vi.spyOn(AbortSignal, "timeout").mockReturnValue(deadline);
    const server = createServer((_request, response) => {
      setTimeout(() => response.end("[]"), 250);
    });
    server.listen(0, "127.0.0.1");
    await once(server, "listening");
    try {
      const address = server.address();
      if (!address || typeof address === "string") throw new Error("No server port");
      const baseUrl = `http://127.0.0.1:${address.port}`;
      const request = kind === "typed"
        ? createApiClient(baseUrl).GET("/api/v1/meetings/upcoming")
        : fetchJoinReadiness(baseUrl);
      await expect(request).rejects.toMatchObject({ name: "TimeoutError" });
      expect(AbortSignal.timeout).toHaveBeenCalledWith(10_000);
    } finally {
      server.closeAllConnections();
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
  });

  it("preserves Request options, JSON body, cookies and CSRF headers", async () => {
    const transport = vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response("{}", {
      headers: { "Content-Type": "application/json" },
    }));
    const controller = new AbortController();
    await createApiClient("https://backend.example/api/v1").POST(
      "/api/v1/rooms", {
        body: { name: "Room", tenantId: "tenant-1", configSetId: "config-1" },
        headers: { Cookie: "JSESSIONID=session", "X-XSRF-TOKEN": "csrf", "Idempotency-Key": "id-1" },
        signal: controller.signal,
        redirect: "manual", credentials: "include", cache: "no-store",
        referrerPolicy: "no-referrer", keepalive: true,
      },
    );
    const [url, options] = transport.mock.calls[0];
    expect(url).toBe("https://backend.example/api/v1/rooms");
    expect(options).toMatchObject({
      method: "POST", redirect: "manual", credentials: "include", cache: "no-store",
      referrerPolicy: "no-referrer", keepalive: true,
      headers: { Cookie: "JSESSIONID=session", "X-XSRF-TOKEN": "csrf", "Idempotency-Key": "id-1" },
    });
    expect(JSON.parse(options?.body as string)).toEqual({ name: "Room", tenantId: "tenant-1", configSetId: "config-1" });
    controller.abort();
    expect(options?.signal?.aborted).toBe(true);
  });
});
