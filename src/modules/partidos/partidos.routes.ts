import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearPartidoSchema, actualizarPartidoSchema, partidosQuerySchema } from './partidos.validation';
import { crearPartido, actualizarPartido, eliminarPartido, listarPartidos } from './partidos.service';

export const partidosRouter = Router();

// Público: historial filtrable
partidosRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const filtros = partidosQuerySchema.parse(req.query);
    const partidos = await listarPartidos(filtros);
    res.json(partidos);
  }),
);

// Admin: carga de resultado
partidosRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearPartidoSchema.parse(req.body);
    const partido = await crearPartido(input);
    res.status(201).json(partido);
  }),
);

// Admin: eliminación de un partido cargado por error
partidosRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarPartido(Number(req.params.id));
    res.status(204).send();
  }),
);

// Admin: corrección de resultado (o cualquier otro campo del partido)
partidosRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const id = Number(req.params.id);
    const input = actualizarPartidoSchema.parse(req.body);
    const partido = await actualizarPartido(id, input);
    res.json(partido);
  }),
);
