import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { z } from 'zod';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearEquipoSchema, actualizarEquipoSchema } from './equipos.validation';
import {
  listarEquipos,
  obtenerEquipo,
  crearEquipo,
  actualizarEquipo,
  eliminarEquipo,
  obtenerPlantel,
  obtenerStatsEquipoActual,
} from './equipos.service';

export const equiposRouter = Router();

const statsQuerySchema = z.object({
  categoriaId: z.coerce.number().int().positive().optional(),
  zonaId: z.coerce.number().int().positive().optional(),
});

equiposRouter.get(
  '/',
  asyncHandler(async (_req, res) => res.json(await listarEquipos())),
);

equiposRouter.get(
  '/:id',
  asyncHandler(async (req, res) => res.json(await obtenerEquipo(Number(req.params.id)))),
);

equiposRouter.get(
  '/:id/jugadores',
  asyncHandler(async (req, res) => res.json(await obtenerPlantel(Number(req.params.id)))),
);

equiposRouter.get(
  '/:id/stats',
  asyncHandler(async (req, res) => {
    const { categoriaId, zonaId } = statsQuerySchema.parse(req.query);
    res.json(await obtenerStatsEquipoActual(Number(req.params.id), categoriaId, zonaId));
  }),
);

equiposRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearEquipoSchema.parse(req.body);
    res.status(201).json(await crearEquipo(input));
  }),
);

equiposRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarEquipoSchema.parse(req.body);
    res.json(await actualizarEquipo(Number(req.params.id), input));
  }),
);

equiposRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarEquipo(Number(req.params.id));
    res.status(204).send();
  }),
);
