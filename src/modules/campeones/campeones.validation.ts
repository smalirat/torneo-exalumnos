import { z } from 'zod';

// La carga manual siempre sabe qué instancia es (Apertura / Clausura /
// Final anual). DESCONOCIDO es solo para la historia importada del Excel.
const instanciaManualSchema = z.enum(['APERTURA', 'CLAUSURA', 'FINAL_ANUAL']);

export const crearCampeonSchema = z.object({
  anio: z.coerce.number().int().min(2000).max(2100),
  categoriaId: z.coerce.number().int().positive(),
  equipoId: z.coerce.number().int().positive(),
  instancia: instanciaManualSchema,
});
export type CrearCampeonInput = z.infer<typeof crearCampeonSchema>;

export const actualizarCampeonSchema = z.object({
  equipoId: z.coerce.number().int().positive().optional(),
  instancia: instanciaManualSchema.optional(),
});
export type ActualizarCampeonInput = z.infer<typeof actualizarCampeonSchema>;

export const historialCampeonesQuerySchema = z.object({
  categoriaId: z.coerce.number().int().positive().optional(),
});
