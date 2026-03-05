export interface UserProfileResponse {
  subjectId: string;
  tenantId: string;
  fullName: string;
  organization: string;
  position: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpsertProfileRequest {
  fullName: string;
  organization: string;
  position: string;
}

export interface ProfileErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}
