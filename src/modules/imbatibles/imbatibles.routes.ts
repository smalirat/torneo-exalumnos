import { Router } from 'express';
import { asyncHandler } from '../../middleware/asyncHandler';
import { imbatiblesQuerySchema } from './imbatibles.validation';
import { listarImbatibles } from './imbatibles.service';

export const imbatiblesRouter = Router();

imbatiblesRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { torneoId } = imbatiblesQuerySchema.parse(req.query);
    res.json(await listarImbatibles(torneoId));
  }),
);
