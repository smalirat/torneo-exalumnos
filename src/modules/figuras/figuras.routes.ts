import { Router } from 'express';
import { asyncHandler } from '../../middleware/asyncHandler';
import { figurasQuerySchema } from './figuras.validation';
import { listarFiguras } from './figuras.service';

export const figurasRouter = Router();

figurasRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { torneoId } = figurasQuerySchema.parse(req.query);
    res.json(await listarFiguras(torneoId));
  }),
);
