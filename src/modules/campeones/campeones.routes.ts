import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { crearCampeonSchema, actualizarCampeonSchema, historialCampeonesQuerySchema } from './campeones.validation';
import { crearCampeon, actualizarCampeon, eliminarCampeon, obtenerHistorialCampeones } from './campeones.service';

export const campeonesRouter = Router();

campeonesRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = crearCampeonSchema.parse(req.body);
    res.status(201).json(await crearCampeon(input));
  }),
);

campeonesRouter.put(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarCampeonSchema.parse(req.body);
    res.json(await actualizarCampeon(Number(req.params.id), input));
  }),
);

campeonesRouter.delete(
  '/:id',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    await eliminarCampeon(Number(req.params.id));
    res.status(204).send();
  }),
);

// Público — GET /historial-campeones (spec). Devuelve el historial completo
// MÁS el ranking de máximos campeones ya calculado, para no obligar al
// frontend a hacer el group-by client-side.
export const historialCampeonesRouter = Router();

historialCampeonesRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { categoriaId } = historialCampeonesQuerySchema.parse(req.query);
    res.json(await obtenerHistorialCampeones(categoriaId));
  }),
);
