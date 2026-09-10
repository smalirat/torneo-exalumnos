import { Router } from 'express';
import fs from 'fs';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { ValidationError } from '../../utils/AppError';
import { crearCampeonSchema, actualizarCampeonSchema, historialCampeonesQuerySchema } from './campeones.validation';
import { crearCampeon, actualizarCampeon, eliminarCampeon, obtenerHistorialCampeones } from './campeones.service';
import { upload } from '../excel/imports/excelImport.routes';
import { importarHistoria } from '../excel/imports/historiaImport.service';

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

// Importa la hoja HISTORIA de un .xlsx: crea Temporada + Torneo +
// Categoría por campeón y los equipos viejos (solo nombre, sin
// jugadores). Idempotente: reimportar no duplica.
campeonesRouter.post(
  '/importar-historia',
  requireRole(RolUsuario.ADMIN),
  upload.single('archivo'),
  asyncHandler(async (req, res) => {
    if (!req.file) {
      throw new ValidationError('Falta el archivo (campo "archivo" en el form-data)');
    }
    const resumen = await importarHistoria(fs.readFileSync(req.file.path));
    res.status(201).json(resumen);
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
