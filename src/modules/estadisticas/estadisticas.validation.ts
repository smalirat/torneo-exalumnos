import { z } from 'zod';

// POST /estadisticas — actualizar las tres estadísticas anuales de un jugador
// en un torneo (Goleador.goles, Figura.veces y Imbatible.golesRecibidos).
// Los valores se interpretan como DELTA: se suman al contador existente
// (negativos para corregir una carga errónea).
export const actualizarEstadisticasSchema = z.object({
  jugadorId: z.coerce.number().int().positive(),
  torneoId: z.coerce.number().int().positive(),
  goles: z.coerce.number().int().optional(),
  mvp: z.coerce.number().int().optional(),
  golesRecibidos: z.coerce.number().int().optional(),
});
export type ActualizarEstadisticasInput = z.infer<typeof actualizarEstadisticasSchema>;