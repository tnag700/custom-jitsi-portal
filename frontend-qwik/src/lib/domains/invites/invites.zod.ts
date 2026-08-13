import { z } from "zod";

export const createInviteSchema = z.object({
  role: z.literal("participant"),
  maxUses: z.coerce.number().int().min(1).default(1),
  expiresInHours: z.coerce.number().int().min(1).max(168),
});

export const exchangeInviteSchema = z.object({
  inviteToken: z.string().min(1, "inviteToken обязателен"),
  displayName: z
    .string()
    .trim()
    .min(2, "Введите имя длиной не менее 2 символов")
    .max(80, "Имя не должно превышать 80 символов")
    .refine(
      (value) =>
        Array.from(value).every((character) => {
          const codePoint = character.codePointAt(0) ?? 0;
          return codePoint >= 32 && codePoint !== 127;
        }),
      {
        message: "Имя содержит недопустимые управляющие символы",
      },
    ),
});

export const inviteExchangeResponseSchema = z.object({
  joinUrl: z.string().min(1),
  expiresAt: z.string().datetime({ offset: true }),
  role: z.literal("participant"),
  meetingId: z.string().min(1),
});
