import { z } from 'zod';

export const figurasQuerySchema = z.object({
  torneoId: z.coerce.number().int().positive({ message: 'torneoId es obligatorio' }),
});
