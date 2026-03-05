import { createContextId } from "@qwik.dev/core";
import type { AuthErrorPayload, SafeUserProfile } from "./types";

export interface AuthStore {
  isAuthenticated: boolean;
  profile: SafeUserProfile | null;
  error: AuthErrorPayload | null;
}

export const AuthContext = createContextId<AuthStore>("auth-context");
