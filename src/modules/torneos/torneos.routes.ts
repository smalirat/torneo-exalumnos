import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import {
  crearTorneoSchema,
  actualizarTorneoSchema,
  listarTorneosQuerySchema,
} from './torneos.validation';
import { listarTorneos, obtenerTorneo, crearTorneo, actualizarTorneo, eliminarTorneo } from './torneos.service';

export const torneosRouter = Router();

torneosRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { temporadaId } = listarTorneosQuerySchema.parse(req.query);
    res.json(await listarTorneos(temporadaId));
  }),
);

torneosRouter.get(
  '/:id',
  asyncHandler(async (req, res) => res.json(await obtenerTorneo(Number(req.params.id)))),
);

torneosRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearTorneoSchema.parse(req.body);
    res.status(201).json(await crearTorneo(input));
  }),
);

torneosRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarTorneoSchema.parse(req.body);
    res.json(await actualizarTorneo(Number(req.params.id), input));
  }),
);

torneosRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarTorneo(Number(req.params.id));
    res.status(204).send();
  }),
);
