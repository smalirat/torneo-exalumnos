import { z } from 'zod';
import { NombreTorneo } from '@prisma/client';

export const crearTorneoSchema = z.object({
  temporadaId: z.coerce.number().int().positive(),
  nombre: z.nativeEnum(NombreTorneo),
});
export type CrearTorneoInput = z.infer<typeof crearTorneoSchema>;

export const actualizarTorneoSchema = z.object({
  nombre: z.nativeEnum(NombreTorneo).optional(),
});
export type ActualizarTorneoInput = z.infer<typeof actualizarTorneoSchema>;

export const listarTorneosQuerySchema = z.object({
  temporadaId: z.coerce.number().int().positive().optional(),
});
