import { Router } from 'express';
import { asyncHandler } from '../../middleware/asyncHandler';
import { goleadoresQuerySchema } from './goleadores.validation';
import { listarGoleadores } from './goleadores.service';

export const goleadoresRouter = Router();

goleadoresRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { torneoId } = goleadoresQuerySchema.parse(req.query);
    res.json(await listarGoleadores(torneoId));
  }),
);
