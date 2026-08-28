import { z } from 'zod';

export const crearTemporadaSchema = z.object({
  anio: z.coerce.number().int().min(2000).max(2100),
});
export type CrearTemporadaInput = z.infer<typeof crearTemporadaSchema>;

export const actualizarTemporadaSchema = crearTemporadaSchema.partial();
export type ActualizarTemporadaInput = z.infer<typeof actualizarTemporadaSchema>;
