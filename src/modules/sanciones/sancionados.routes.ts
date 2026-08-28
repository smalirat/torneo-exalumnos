import { Router } from 'express';
import { asyncHandler } from '../../middleware/asyncHandler';
import { sancionadosQuerySchema } from './sanciones.validation';
import { listarSancionados } from './sanciones.service';

export const sancionadosRouter = Router();

sancionadosRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { torneoId, pendiente } = sancionadosQuerySchema.parse(req.query);
    res.json(await listarSancionados(torneoId, pendiente));
  }),
);
