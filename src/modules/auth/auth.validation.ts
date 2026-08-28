import { z } from 'zod';

export const loginSchema = z.object({
  username: z.string().trim().min(1, 'username es obligatorio'),
  password: z.string().min(1, 'password es obligatorio'),
});
export type LoginInput = z.infer<typeof loginSchema>;
