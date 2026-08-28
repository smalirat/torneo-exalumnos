import { z } from 'zod';

export const imbatiblesQuerySchema = z.object({
  torneoId: z.coerce.number().int().positive({ message: 'torneoId es obligatorio' }),
});
