import { z } from 'zod';

export const crearCampeonSchema = z.object({
  anio: z.coerce.number().int().min(2000).max(2100),
  categoriaId: z.coerce.number().int().positive(),
  equipoId: z.coerce.number().int().positive(),
});
export type CrearCampeonInput = z.infer<typeof crearCampeonSchema>;

export const actualizarCampeonSchema = z.object({
  equipoId: z.coerce.number().int().positive().optional(),
});
export type ActualizarCampeonInput = z.infer<typeof actualizarCampeonSchema>;

export const historialCampeonesQuerySchema = z.object({
  categoriaId: z.coerce.number().int().positive().optional(),
});
