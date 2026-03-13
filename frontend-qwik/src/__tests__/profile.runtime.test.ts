import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ProfileServiceError,
  adaptProfileProblemDetails,
  fetchMyProfile,
  upsertMyProfile,
} from "../lib/domains/profile/profile.service";
import { profileFormSchema } from "../lib/domains/profile/profile.schema";

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("profile schema runtime", () => {
  it("rejects whitespace-only values", () => {
    const result = profileFormSchema.safeParse({
      fullName: "   ",
      organization: "  ",
      position: " ",
    });

    expect(result.success).toBe(false);
  });

  it("trims values on successful parse", () => {
    const result = profileFormSchema.safeParse({
      fullName: "  Jane Doe  ",
      organization: "  Acme  ",
      position: "  Lead  ",
    });

    expect(result.success).toBe(true);
    if (!result.success) return;
    expect(result.data).toEqual({
      fullName: "Jane Doe",
      organization: "Acme",
      position: "Lead",
    });
  });
});

describe("profile.service runtime", () => {
  it("maps RFC7807 fields and preserves explicit errorCode", async () => {
    const response = jsonResponse(
      {
        title: "Validation failed",
        detail: "Position is too short",
        errorCode: "PROFILE_VALIDATION_FAILED",
        traceId: "trace-123",
      },
      400,
    );

    await expect(adaptProfileProblemDetails(response)).resolves.toEqual({
      title: "Validation failed",
      detail: "Position is too short",
      errorCode: "PROFILE_VALIDATION_FAILED",
      traceId: "trace-123",
    });
  });

  it("returns null for 404 (first-run)", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 404));

    const result = await fetchMyProfile("sess-1", "http://localhost:8080/api/v1");

    expect(result).toBeNull();
  });

  it("calls GET /profile/me with JSESSIONID cookie", async () => {
    const payload = {
      subjectId: "sub-1",
      tenantId: "tenant-1",
      fullName: "Jane Doe",
      organization: "Acme",
      position: "Lead",
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:10:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    const result = await fetchMyProfile("sess-123", "http://localhost:8080/api/v1");

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/profile/me",
      expect.objectContaining({
        method: "GET",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-123",
        }),
      }),
    );
    expect(result).toEqual(payload);
  });

  it("calls PUT /profile/me with CSRF header and body", async () => {
    const payload = {
      subjectId: "sub-1",
      tenantId: "tenant-1",
      fullName: "Jane Doe",
      organization: "Acme",
      position: "Lead",
      createdAt: "2026-03-03T10:00:00Z",
      updatedAt: "2026-03-03T10:10:00Z",
    };
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse(payload, 200));

    await upsertMyProfile("sess-123", "http://localhost:8080/api/v1", "csrf-456", {
      fullName: "Jane Doe",
      organization: "Acme",
      position: "Lead",
    });

    expect(fetchMock).toHaveBeenCalledWith(
      "http://localhost:8080/api/v1/profile/me",
      expect.objectContaining({
        method: "PUT",
        headers: expect.objectContaining({
          Cookie: "JSESSIONID=sess-123; XSRF-TOKEN=csrf-456",
          "X-XSRF-TOKEN": "csrf-456",
        }),
      }),
    );
    const callArgs = fetchMock.mock.calls[0][1] as RequestInit;
    expect(callArgs.body).toContain("Jane Doe");
  });

  it("throws ProfileServiceError with fallback code for 5xx", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 503));

    await expect(
      upsertMyProfile("sess", "http://localhost:8080/api/v1", "csrf", {
        fullName: "Jane Doe",
        organization: "Acme",
        position: "Lead",
      }),
    ).rejects.toBeInstanceOf(ProfileServiceError);

    await expect(
      upsertMyProfile("sess", "http://localhost:8080/api/v1", "csrf", {
        fullName: "Jane Doe",
        organization: "Acme",
        position: "Lead",
      }),
    ).rejects.toMatchObject({
      payload: { errorCode: "PROFILE_SERVICE_UNAVAILABLE" },
    });
  });

  it("fetchMyProfile throws ProfileServiceError with AUTH_REQUIRED on 401", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      jsonResponse({ errorCode: "AUTH_REQUIRED", title: "Unauthorized", detail: "No session" }, 401),
    );

    await expect(
      fetchMyProfile("expired-sess", "http://localhost:8080/api/v1"),
    ).rejects.toBeInstanceOf(ProfileServiceError);

    await expect(
      fetchMyProfile("expired-sess", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      payload: { errorCode: "AUTH_REQUIRED" },
    });
  });

  it("fetchMyProfile throws ProfileServiceError with fallback code on 500", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(jsonResponse({}, 500));

    await expect(
      fetchMyProfile("sess", "http://localhost:8080/api/v1"),
    ).rejects.toBeInstanceOf(ProfileServiceError);

    await expect(
      fetchMyProfile("sess", "http://localhost:8080/api/v1"),
    ).rejects.toMatchObject({
      payload: { errorCode: "PROFILE_SERVICE_UNAVAILABLE" },
    });
  });
});
