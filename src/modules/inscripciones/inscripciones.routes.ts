import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearInscripcionSchema, listarInscripcionesQuerySchema } from '../inscripciones/inscripciones.validation';
import { listarInscripciones, crearInscripcion, eliminarInscripcion } from '../inscripciones/inscripciones.service';

export const inscripcionesRouter = Router();

inscripcionesRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const filtros = listarInscripcionesQuerySchema.parse(req.query);
    res.json(await listarInscripciones(filtros));
  }),
);

inscripcionesRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearInscripcionSchema.parse(req.body);
    res.status(201).json(await crearInscripcion(input));
  }),
);

inscripcionesRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarInscripcion(Number(req.params.id));
    res.status(204).send();
  }),
);
