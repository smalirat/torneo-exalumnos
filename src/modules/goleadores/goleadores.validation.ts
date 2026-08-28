import { z } from 'zod';

export const goleadoresQuerySchema = z.object({
  torneoId: z.coerce.number().int().positive({ message: 'torneoId es obligatorio' }),
});
