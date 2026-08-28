import { z } from 'zod';

export const crearZonaSchema = z.object({
  categoriaId: z.coerce.number().int().positive(),
  nombre: z.string().trim().min(1).max(50),
});
export type CrearZonaInput = z.infer<typeof crearZonaSchema>;

export const actualizarZonaSchema = z.object({
  nombre: z.string().trim().min(1).max(50).optional(),
});
export type ActualizarZonaInput = z.infer<typeof actualizarZonaSchema>;

export const listarZonasQuerySchema = z.object({
  categoriaId: z.coerce.number().int().positive().optional(),
});
