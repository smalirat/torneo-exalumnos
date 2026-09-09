import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearSancionSchema, actualizarSancionSchema } from './sanciones.validation';
import { crearSancion, actualizarSancion, eliminarSancion } from './sanciones.service';

export const sancionesRouter = Router();

sancionesRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearSancionSchema.parse(req.body);
    res.status(201).json(await crearSancion(input));
  }),
);

sancionesRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarSancionSchema.parse(req.body);
    res.json(await actualizarSancion(Number(req.params.id), input));
  }),
);

sancionesRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarSancion(Number(req.params.id));
    res.status(204).send();
  }),
);
