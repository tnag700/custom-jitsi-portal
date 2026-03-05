export interface SafeUserProfile {
  id: string;
  displayName: string;
  email: string;
  tenant: string;
  claims: string[];
}

export interface AuthErrorPayload {
  title: string;
  reason: string;
  actions: string;
  errorCode: string;
}

export type AuthRole = "participant" | "moderator" | "host";
