import { describe, expect, it } from "vitest";
import {
  synchronizeAuthStore,
  type AuthStore,
} from "../lib/domains/auth/auth-context";
import type { SafeUserProfile } from "../lib/domains/auth/types";

describe("auth context synchronization", () => {
  it("clears authenticated navigation state after a public-route transition", () => {
    const store: AuthStore = {
      isAuthenticated: true,
      profile: {
        id: "admin-id",
        displayName: "Administrator",
        email: "admin@example.test",
        claims: ["admin"],
        tenant: "tenant-dev",
      },
      error: {
        title: "Old error",
        reason: "Old reason",
        actions: "Old action",
        errorCode: "OLD_ERROR",
      },
    };

    synchronizeAuthStore(store, null);

    expect(store).toEqual({
      isAuthenticated: false,
      profile: null,
      error: null,
    });
  });

  it("refreshes the authenticated profile after a route-loader transition", () => {
    const store: AuthStore = {
      isAuthenticated: false,
      profile: null,
      error: null,
    };
    const profile: SafeUserProfile = {
      id: "participant-id",
      displayName: "Participant",
      email: "participant@example.test",
      claims: ["participant"],
      tenant: "tenant-dev",
    };

    synchronizeAuthStore(store, profile);

    expect(store.isAuthenticated).toBe(true);
    expect(store.profile).toBe(profile);
    expect(store.error).toBeNull();
  });
});
