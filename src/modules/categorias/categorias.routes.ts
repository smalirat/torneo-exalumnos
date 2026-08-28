import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import {
  crearCategoriaSchema,
  actualizarCategoriaSchema,
  listarCategoriasQuerySchema,
} from './categorias.validation';
import {
  listarCategorias,
  obtenerCategoria,
  crearCategoria,
  actualizarCategoria,
  eliminarCategoria,
} from './categorias.service';

export const categoriasRouter = Router();

categoriasRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { torneoId } = listarCategoriasQuerySchema.parse(req.query);
    res.json(await listarCategorias(torneoId));
  }),
);

categoriasRouter.get(
  '/:id',
  asyncHandler(async (req, res) => res.json(await obtenerCategoria(Number(req.params.id)))),
);

categoriasRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearCategoriaSchema.parse(req.body);
    res.status(201).json(await crearCategoria(input));
  }),
);

categoriasRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarCategoriaSchema.parse(req.body);
    res.json(await actualizarCategoria(Number(req.params.id), input));
  }),
);

categoriasRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarCategoria(Number(req.params.id));
    res.status(204).send();
  }),
);
