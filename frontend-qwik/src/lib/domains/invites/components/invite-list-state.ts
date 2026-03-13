import type { Invite } from "../types";

export type InviteVisibilityFilter = "active" | "all" | "deleted";

export interface InviteListSummary {
  activeCount: number;
  deletedCount: number;
  totalCount: number;
  lastDeletedAt: string | null;
}

function isDeleted(invite: Invite): boolean {
  return Boolean(invite.revokedAt);
}

export function applyInviteListState(
  invites: Invite[],
  visibilityFilter: InviteVisibilityFilter,
): Invite[] {
  if (visibilityFilter === "deleted") {
    return invites.filter((invite) => isDeleted(invite));
  }

  if (visibilityFilter === "active") {
    return invites.filter((invite) => !isDeleted(invite) && invite.valid);
  }

  return invites;
}

export function summarizeInviteList(invites: Invite[]): InviteListSummary {
  const deletedInvites = invites.filter((invite) => isDeleted(invite));
  const lastDeletedAt = deletedInvites.reduce<string | null>((latest, invite) => {
    if (!invite.revokedAt) {
      return latest;
    }

    if (!latest) {
      return invite.revokedAt;
    }

    return new Date(invite.revokedAt).getTime() > new Date(latest).getTime() ? invite.revokedAt : latest;
  }, null);

  return {
    activeCount: invites.filter((invite) => !isDeleted(invite) && invite.valid).length,
    deletedCount: deletedInvites.length,
    totalCount: invites.length,
    lastDeletedAt,
  };
}