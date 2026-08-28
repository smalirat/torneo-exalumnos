import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearTemporadaSchema, actualizarTemporadaSchema } from './temporadas.validation';
import {
  listarTemporadas,
  obtenerTemporada,
  crearTemporada,
  actualizarTemporada,
  eliminarTemporada,
} from './temporadas.service';

export const temporadasRouter = Router();

temporadasRouter.get(
  '/',
  asyncHandler(async (_req, res) => res.json(await listarTemporadas())),
);

temporadasRouter.get(
  '/:id',
  asyncHandler(async (req, res) => res.json(await obtenerTemporada(Number(req.params.id)))),
);

temporadasRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearTemporadaSchema.parse(req.body);
    res.status(201).json(await crearTemporada(input));
  }),
);

temporadasRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarTemporadaSchema.parse(req.body);
    res.json(await actualizarTemporada(Number(req.params.id), input));
  }),
);

temporadasRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarTemporada(Number(req.params.id));
    res.status(204).send();
  }),
);
