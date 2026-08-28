import { z } from 'zod';

export const crearInscripcionSchema = z.object({
  equipoId: z.coerce.number().int().positive(),
  categoriaId: z.coerce.number().int().positive(),
  zonaId: z.coerce.number().int().positive().optional(),
});
export type CrearInscripcionInput = z.infer<typeof crearInscripcionSchema>;

export const listarInscripcionesQuerySchema = z.object({
  equipoId: z.coerce.number().int().positive().optional(),
  categoriaId: z.coerce.number().int().positive().optional(),
  zonaId: z.coerce.number().int().positive().optional(),
});
