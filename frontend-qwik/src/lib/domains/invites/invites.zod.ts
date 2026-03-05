import { z } from "zod";

export const createInviteSchema = z.object({
  role: z.enum(["participant", "moderator"]),
  maxUses: z.coerce.number().int().min(1).default(1),
  expiresInHours: z
    .union([z.literal(""), z.null(), z.undefined(), z.coerce.number().int().min(1).max(168)])
    .transform((value) => {
      if (value === "" || value == null) {
        return undefined;
      }
      return value;
    }),
});

export const exchangeInviteSchema = z.object({
  inviteToken: z.string().min(1, "inviteToken обязателен"),
  displayName: z.string().trim().min(1).optional(),
});
