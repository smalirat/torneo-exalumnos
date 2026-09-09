import { z } from 'zod';

// POST /puntos-restados — descuento de puntos por resolución del tribunal.
export const crearPuntosRestadosSchema = z.object({
  equipoId: z.coerce.number().int().positive(),
  categoriaId: z.coerce.number().int().positive(),
  zonaId: z.coerce.number().int().positive().nullable().optional(),
  puntos: z.coerce.number().int().min(1),
  motivo: z.string().trim().max(500).optional(),
});
export type CrearPuntosRestadosInput = z.infer<typeof crearPuntosRestadosSchema>;