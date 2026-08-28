import { z } from 'zod';

export const crearJugadorSchema = z.object({
  nombre: z.string().trim().min(1).max(100),
  equipoId: z.coerce.number().int().positive().optional(),
});
export type CrearJugadorInput = z.infer<typeof crearJugadorSchema>;

export const actualizarJugadorSchema = z.object({
  nombre: z.string().trim().min(1).max(100).optional(),
  equipoId: z.coerce.number().int().positive().nullable().optional(),
});
export type ActualizarJugadorInput = z.infer<typeof actualizarJugadorSchema>;

export const listarJugadoresQuerySchema = z.object({
  equipoId: z.coerce.number().int().positive().optional(),
});
