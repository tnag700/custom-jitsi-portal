import { z } from "zod";

export const profileFormSchema = z.object({
  fullName: z.string().trim().min(2, "Минимум 2 символа").max(500, "Максимум 500 символов"),
  organization: z.string().trim().min(2, "Минимум 2 символа").max(500, "Максимум 500 символов"),
  position: z.string().trim().min(2, "Минимум 2 символа").max(500, "Максимум 500 символов"),
});
