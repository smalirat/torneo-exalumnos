import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearPuntosPresentismoSchema } from './puntosPresentismo.validation';
import { crearPuntosPresentismo, eliminarPuntosPresentismo } from './puntosPresentismo.service';

export const puntosPresentismoRouter = Router();

puntosPresentismoRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearPuntosPresentismoSchema.parse(req.body);
    res.status(201).json(await crearPuntosPresentismo(input));
  }),
);

puntosPresentismoRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarPuntosPresentismo(Number(req.params.id));
    res.status(204).send();
  }),
);