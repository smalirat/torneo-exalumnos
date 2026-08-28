import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearZonaSchema, actualizarZonaSchema, listarZonasQuerySchema } from './zonas.validation';
import { listarZonas, obtenerZona, crearZona, actualizarZona, eliminarZona } from './zonas.service';

export const zonasRouter = Router();

zonasRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { categoriaId } = listarZonasQuerySchema.parse(req.query);
    res.json(await listarZonas(categoriaId));
  }),
);

zonasRouter.get(
  '/:id',
  asyncHandler(async (req, res) => res.json(await obtenerZona(Number(req.params.id)))),
);

zonasRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearZonaSchema.parse(req.body);
    res.status(201).json(await crearZona(input));
  }),
);

zonasRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarZonaSchema.parse(req.body);
    res.json(await actualizarZona(Number(req.params.id), input));
  }),
);

zonasRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarZona(Number(req.params.id));
    res.status(204).send();
  }),
);
