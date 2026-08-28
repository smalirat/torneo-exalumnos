import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearUsuarioSchema } from './usuarios.validation';
import { listarUsuarios, crearUsuario, eliminarUsuario } from './usuarios.service';

export const usuariosRouter = Router();

// Toda esta ruta es admin-only: gestionar cuentas (crear delegados, etc.)
usuariosRouter.use(requireRole(RolUsuario.ADMIN));

usuariosRouter.get(
  '/',
  asyncHandler(async (_req, res) => res.json(await listarUsuarios())),
);

usuariosRouter.post(
  '/',
  asyncHandler(async (req, res) => {
    const input = crearUsuarioSchema.parse(req.body);
    res.status(201).json(await crearUsuario(input));
  }),
);

usuariosRouter.delete(
  '/:id',
  asyncHandler(async (req, res) => {
    await eliminarUsuario(Number(req.params.id));
    res.status(204).send();
  }),
);
