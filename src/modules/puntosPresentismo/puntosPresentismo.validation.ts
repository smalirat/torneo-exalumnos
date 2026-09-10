import { z } from 'zod';

// POST /puntos-presentismo — puntos de presentismo (PR): se SUMAN a los
// puntos de cancha en la tabla. Típicamente +1 por partido jugado.
export const crearPuntosPresentismoSchema = z.object({
  equipoId: z.coerce.number().int().positive(),
  categoriaId: z.coerce.number().int().positive(),
  zonaId: z.coerce.number().int().positive().nullable().optional(),
  puntos: z.coerce.number().int().min(1),
  motivo: z.string().trim().max(500).optional(),
});
export type CrearPuntosPresentismoInput = z.infer<typeof crearPuntosPresentismoSchema>;