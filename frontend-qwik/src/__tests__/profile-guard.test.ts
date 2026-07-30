import { describe, it, expect } from "vitest";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const SRC_DIR = join(__dirname, "..");

function readSrc(relativePath: string): string {
  const full = join(SRC_DIR, relativePath);
  if (!existsSync(full)) {
    throw new Error(`File not found: ${relativePath}`);
  }
  return readFileSync(full, "utf-8");
}

describe("Profile Guard: types (AC: 1, 2, 4)", () => {
  it("types.ts should define UserProfileResponse, UpsertProfileRequest, ProfileErrorPayload", () => {
    const ts = readSrc("lib/domains/profile/types.ts");
    expect(ts).toContain("UserProfileResponse");
    expect(ts).toContain("UpsertProfileRequest");
    expect(ts).toContain("ProfileErrorPayload");
  });
});

describe("Profile Guard: service (AC: 1, 2, 4, 5)", () => {
  it("profile.service.ts should contain fetchMyProfile, upsertMyProfile, ProfileServiceError", () => {
    const ts = readSrc("lib/domains/profile/profile.service.ts");
    expect(ts).toContain("fetchMyProfile");
    expect(ts).toContain("upsertMyProfile");
    expect(ts).toContain("ProfileServiceError");
  });
});

describe("Profile Guard: components (AC: 1, 2, 3, 5, 6)", () => {
  it("ProfileForm.tsx should contain component$", () => {
    const tsx = readSrc("lib/domains/profile/components/ProfileForm.tsx");
    expect(tsx).toContain("component$");
  });

  it("ProfileForm.tsx should use shared schema and contain loading spinner marker", () => {
    const tsx = readSrc("lib/domains/profile/components/ProfileForm.tsx");
    expect(tsx).toContain("profileFormSchema");
    expect(tsx).toContain("animate-spin");
  });
});

describe("Profile Guard: validation schema (AC: 2, 3)", () => {
  it("profile.schema.ts should define trimmed min/max constraints", () => {
    const ts = readSrc("lib/domains/profile/profile.schema.ts");
    expect(ts).toContain(".trim().min(2");
    expect(ts).toContain(".max(500");
  });
});

describe("Profile Guard: barrel export (AC: all)", () => {
  it("index.ts should exist as profile barrel export", () => {
    const ts = readSrc("lib/domains/profile/index.ts");
    expect(ts).toBeDefined();
  });
});

describe("Profile Guard: route integration (AC: 1, 2, 3, 4, 5, 6)", () => {
  it("profile route should stay a thin composition boundary", () => {
    const tsx = readSrc("routes/profile/index.tsx");
    expect(tsx).toContain("ProfilePage");
    expect(tsx).toContain("useMyProfile");
    expect(tsx).toContain("useUpsertProfile");
    expect(tsx).not.toContain("routeLoader$");
    expect(tsx).not.toContain("routeAction$");
    expect(tsx.split("\n").length).toBeLessThan(40);
  });

  it("profile loader and action should own backend requests", () => {
    const loader = readSrc("routes/profile/loader.ts");
    const action = readSrc("routes/profile/action.ts");
    expect(loader).toContain("routeLoader$");
    expect(loader).toContain("fetchMyProfile");
    expect(action).toContain("routeAction$");
    expect(action).toContain("upsertMyProfile");
  });

  it("profile loader and action should use shared server request contexts", () => {
    const loader = readSrc("routes/profile/loader.ts");
    const action = readSrc("routes/profile/action.ts");
    expect(loader).toContain("buildServerRequestContext");
    expect(action).toContain("buildMutationRequestContext");
  });

  it("profile action should validate while page surfaces success via toast", () => {
    const action = readSrc("routes/profile/action.ts");
    const page = readSrc("routes/profile/profile-page.tsx");
    expect(action).toContain("profileFormSchema");
    expect(page).toContain("showToast$");
    expect(page).toContain("Профиль сохранён");
  });

  it("profile page should explain data use and isolate role management", () => {
    const page = readSrc("routes/profile/profile-page.tsx");
    expect(page).toContain("Где используются данные");
    expect(page).toContain("Системные");
    expect(page).toContain("роли и права доступа здесь не изменяются");
    expect(page).toContain("Изменить системную роль через профиль нельзя");
  });
});
