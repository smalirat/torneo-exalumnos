import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearPuntosRestadosSchema } from './puntosRestados.validation';
import { crearPuntosRestados, eliminarPuntosRestados } from './puntosRestados.service';

export const puntosRestadosRouter = Router();

puntosRestadosRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearPuntosRestadosSchema.parse(req.body);
    res.status(201).json(await crearPuntosRestados(input));
  }),
);

puntosRestadosRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarPuntosRestados(Number(req.params.id));
    res.status(204).send();
  }),
);