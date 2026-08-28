import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import {
  crearJugadorSchema,
  actualizarJugadorSchema,
  listarJugadoresQuerySchema,
} from './jugadores.validation';
import {
  listarJugadores,
  obtenerJugador,
  crearJugador,
  actualizarJugador,
  eliminarJugador,
} from './jugadores.service';

export const jugadoresRouter = Router();

jugadoresRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { equipoId } = listarJugadoresQuerySchema.parse(req.query);
    res.json(await listarJugadores(equipoId));
  }),
);

jugadoresRouter.get(
  '/:id',
  asyncHandler(async (req, res) => res.json(await obtenerJugador(Number(req.params.id)))),
);

jugadoresRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearJugadorSchema.parse(req.body);
    res.status(201).json(await crearJugador(input));
  }),
);

jugadoresRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarJugadorSchema.parse(req.body);
    res.json(await actualizarJugador(Number(req.params.id), input));
  }),
);

jugadoresRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarJugador(Number(req.params.id));
    res.status(204).send();
  }),
);
