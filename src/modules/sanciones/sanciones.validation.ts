import { z } from 'zod';

// Tu Excel real mostró que hay 2 tipos: EXPULSADOS (roja directa) y
// AMONESTADOS (amarillas que acumulan: 4 = 1 fecha, 8 = 2 fechas). Por ahora
// el admin carga fechasSuspension a mano en los dos casos — la lógica de
// "contar amarillas automáticamente" la dejamos para el importador de Excel
// (Parte 7), que es de donde sale ese dato en la realidad.
export const tipoTarjetaSchema = z.enum(['ROJA', 'AMARILLA']);

export const crearSancionSchema = z.object({
  jugadorId: z.coerce.number().int().positive(),
  equipoId: z.coerce.number().int().positive(),
  torneoId: z.coerce.number().int().positive(),
  tipoTarjeta: tipoTarjetaSchema,
  fechasSuspension: z.coerce.number().int().min(0),
  observaciones: z.string().trim().max(500).optional(),
});
export type CrearSancionInput = z.infer<typeof crearSancionSchema>;

export const actualizarSancionSchema = z.object({
  fechasSuspension: z.coerce.number().int().min(0).optional(),
  cumplida: z.boolean().optional(),
  pendiente: z.boolean().optional(),
  observaciones: z.string().trim().max(500).optional(),
});
export type ActualizarSancionInput = z.infer<typeof actualizarSancionSchema>;

export const sancionadosQuerySchema = z.object({
  torneoId: z.coerce.number().int().positive({ message: 'torneoId es obligatorio' }),
  pendiente: z.coerce.boolean().optional(),
});
