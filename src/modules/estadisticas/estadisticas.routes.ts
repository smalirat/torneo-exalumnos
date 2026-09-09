import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { actualizarEstadisticasSchema } from './estadisticas.validation';
import { actualizarEstadisticas } from './estadisticas.service';

export const estadisticasRouter = Router();

estadisticasRouter.post(
  '/',
  requireRole(RolUsuario.ADMIN),
  asyncHandler(async (req, res) => {
    const input = actualizarEstadisticasSchema.parse(req.body);
    res.json(await actualizarEstadisticas(input));
  }),
);