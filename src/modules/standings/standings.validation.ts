import { z } from 'zod';

// GET /standings?categoriaId=&zonaId=
// zonaId es opcional: las categorías sin zonas simplemente no lo mandan.
export const standingsQuerySchema = z.object({
  categoriaId: z.coerce.number().int().positive({ message: 'categoriaId es obligatorio y debe ser un entero positivo' }),
  zonaId: z.coerce.number().int().positive().optional(),
});

export type StandingsQuery = z.infer<typeof standingsQuerySchema>;
