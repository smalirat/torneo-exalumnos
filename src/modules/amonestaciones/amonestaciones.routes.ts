import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { registrarAmonestacionSchema } from './amonestaciones.validation';
import { eliminarAmonestacion, registrarAmonestacion } from './amonestaciones.service';

export const amonestacionesRouter = Router();

amonestacionesRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = registrarAmonestacionSchema.parse(req.body);
    res.status(201).json(await registrarAmonestacion(input));
  }),
);

amonestacionesRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarAmonestacion(Number(req.params.id));
    res.status(204).send();
  }),
);