import { Router } from 'express';
import { asyncHandler } from '../../middleware/asyncHandler';
import { standingsQuerySchema } from './standings.validation';
import { obtenerStandings } from './standings.service';

export const standingsRouter = Router();

// Público, sin login (ver spec: "Usuario/público: solo lectura").
standingsRouter.get(
  '/',
  asyncHandler(async (req, res) => {
    const { categoriaId, zonaId } = standingsQuerySchema.parse(req.query);
    const tabla = await obtenerStandings(categoriaId, zonaId);
    res.json(tabla);
  }),
);
