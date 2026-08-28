import { z } from 'zod';
import { EstadoPartido } from '@prisma/client';

// POST /partidos
export const crearPartidoSchema = z
  .object({
    categoriaId: z.coerce.number().int().positive(),
    zonaId: z.coerce.number().int().positive().optional(),
    equipoLocalId: z.coerce.number().int().positive(),
    equipoVisitanteId: z.coerce.number().int().positive(),
    golesLocal: z.coerce.number().int().min(0).optional(),
    golesVisitante: z.coerce.number().int().min(0).optional(),
    fecha: z.coerce.date(),
    jornada: z.coerce.number().int().positive(),
    campo: z.string().trim().min(1).optional(),
    cancha: z.string().trim().min(1).optional(),
    horario: z.string().trim().min(1).optional(),
    estado: z.nativeEnum(EstadoPartido).optional(),
  })
  .refine((data) => data.equipoLocalId !== data.equipoVisitanteId, {
    message: 'El equipo local y el equipo visitante no pueden ser el mismo',
    path: ['equipoVisitanteId'],
  })
  .refine(
    (data) =>
      (data.golesLocal === undefined) === (data.golesVisitante === undefined),
    {
      message: 'golesLocal y golesVisitante tienen que cargarse juntos (los dos o ninguno)',
      path: ['golesVisitante'],
    },
  );

export type CrearPartidoInput = z.infer<typeof crearPartidoSchema>;

// PUT /partidos/{id} — todo opcional, es un update parcial
export const actualizarPartidoSchema = z
  .object({
    categoriaId: z.coerce.number().int().positive().optional(),
    zonaId: z.coerce.number().int().positive().optional(),
    equipoLocalId: z.coerce.number().int().positive().optional(),
    equipoVisitanteId: z.coerce.number().int().positive().optional(),
    golesLocal: z.coerce.number().int().min(0).nullable().optional(),
    golesVisitante: z.coerce.number().int().min(0).nullable().optional(),
    fecha: z.coerce.date().optional(),
    jornada: z.coerce.number().int().positive().optional(),
    campo: z.string().trim().min(1).optional(),
    cancha: z.string().trim().min(1).optional(),
    horario: z.string().trim().min(1).optional(),
    estado: z.nativeEnum(EstadoPartido).optional(),
  })
  .refine(
    (data) =>
      data.equipoLocalId === undefined ||
      data.equipoVisitanteId === undefined ||
      data.equipoLocalId !== data.equipoVisitanteId,
    { message: 'El equipo local y el equipo visitante no pueden ser el mismo', path: ['equipoVisitanteId'] },
  );

export type ActualizarPartidoInput = z.infer<typeof actualizarPartidoSchema>;

// GET /partidos?equipoId=&categoriaId=&jornada=
export const partidosQuerySchema = z.object({
  equipoId: z.coerce.number().int().positive().optional(),
  categoriaId: z.coerce.number().int().positive().optional(),
  jornada: z.coerce.number().int().positive().optional(),
});

export type PartidosQuery = z.infer<typeof partidosQuerySchema>;
