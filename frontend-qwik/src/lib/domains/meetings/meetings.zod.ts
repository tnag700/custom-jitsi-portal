import { z } from "zod";

const roleSchema = z.enum(["host", "moderator", "participant"]);

const isoDateTimeSchema = z
  .string()
  .datetime({ offset: true, message: "Используйте ISO datetime (UTC)" });

const booleanStringSchema = z.union([
  z.boolean(),
  z.string().transform((val) => val === "true"),
]);

export const createMeetingSchema = z
  .object({
    title: z.string().min(1, "Название обязательно").max(255, "Макс. 255 символов"),
    description: z.string().max(1000, "Макс. 1000 символов").optional(),
    meetingType: z.string().min(1, "Тип встречи обязателен"),
    startsAt: isoDateTimeSchema,
    endsAt: isoDateTimeSchema,
    allowGuests: booleanStringSchema.optional(),
    recordingEnabled: booleanStringSchema.optional(),
  })
  .refine((value) => new Date(value.startsAt).getTime() < new Date(value.endsAt).getTime(), {
    message: "Время начала должно быть раньше времени окончания",
    path: ["endsAt"],
  });

export const updateMeetingSchema = z
  .object({
    title: z.string().min(1, "Название обязательно").max(255, "Макс. 255 символов").optional(),
    description: z.string().max(1000, "Макс. 1000 символов").optional(),
    meetingType: z.string().min(1, "Тип встречи обязателен").optional(),
    startsAt: isoDateTimeSchema.optional(),
    endsAt: isoDateTimeSchema.optional(),
    allowGuests: booleanStringSchema.optional(),
    recordingEnabled: booleanStringSchema.optional(),
  })
  .refine(
    (value) => {
      if (!value.startsAt || !value.endsAt) {
        return true;
      }
      return new Date(value.startsAt).getTime() < new Date(value.endsAt).getTime();
    },
    {
      message: "Время начала должно быть раньше времени окончания",
      path: ["endsAt"],
    },
  );

export const assignParticipantSchema = z.object({
  subjectId: z.string().min(1, "subjectId обязателен"),
  role: roleSchema,
});

export const bulkAssignParticipantsSchema = z.object({
  subjectIds: z.array(z.string().min(1, "subjectId обязателен")).min(1, "Выберите хотя бы одного пользователя"),
  defaultRole: roleSchema,
});

export const updateParticipantRoleSchema = z.object({
  role: roleSchema,
});
