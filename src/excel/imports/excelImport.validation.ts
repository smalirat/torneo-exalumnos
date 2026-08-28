import { z } from 'zod';

export const importarExcelParamsSchema = z.object({
  id: z.coerce.number().int().positive(),
});
