export interface Room {
  roomId: string;
  name: string;
  description: string | null;
  tenantId: string;
  configSetId: string;
  status: "active" | "closed";
  createdAt: string;
  updatedAt: string;
}

export interface PagedRoomResponse {
  content: Room[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateRoomRequest {
  name: string;
  description?: string;
  tenantId: string;
  configSetId: string;
}

export interface UpdateRoomRequest {
  name?: string;
  description?: string;
  configSetId?: string;
}

export interface RoomErrorPayload {
  title: string;
  detail: string;
  errorCode: string;
  traceId?: string;
}
