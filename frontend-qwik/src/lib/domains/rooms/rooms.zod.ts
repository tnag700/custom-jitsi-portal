import { z } from "zod";

export const createRoomSchema = z.object({
  name: z.string().min(1, "Название обязательно").max(255, "Макс. 255 символов"),
  description: z.string().max(1000, "Макс. 1000 символов").optional(),
  configSetId: z.string().min(1, "Конфигурация обязательна"),
});

export const updateRoomSchema = z.object({
  name: z.string().min(1, "Название обязательно").max(255, "Макс. 255 символов").optional(),
  description: z.string().max(1000, "Макс. 1000 символов").optional(),
  configSetId: z.string().min(1, "Конфигурация обязательна").optional(),
});
