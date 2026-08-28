import fs from 'fs';
import path from 'path';
import { Router } from 'express';
import multer from 'multer';
import { RolUsuario } from '@prisma/client';
import { asyncHandler } from '../../middleware/asyncHandler';
import { requireRole } from '../../middleware/auth';
import { ValidationError } from '../../utils/AppError';
import { importarExcelParamsSchema } from './excelImport.validation';
import { importarExcel } from './excelImport.service';

const DIRECTORIO_UPLOADS = path.resolve(process.cwd(), 'uploads', 'excel');
fs.mkdirSync(DIRECTORIO_UPLOADS, { recursive: true });

const storage = multer.diskStorage({
  destination: DIRECTORIO_UPLOADS,
  filename: (_req, file, cb) => {
    const timestamp = Date.now();
    const nombreSeguro = file.originalname.replace(/[^a-zA-Z0-9.\-_]/g, '_');
    cb(null, `${timestamp}-${nombreSeguro}`);
  },
});

const upload = multer({
  storage,
  limits: { fileSize: 10 * 1024 * 1024 }, // 10MB, de sobra para este uso
  fileFilter: (_req, file, cb) => {
    const extensionValida = /\.xlsx$/i.test(file.originalname);
    if (!extensionValida) {
      cb(new ValidationError('El archivo tiene que ser un .xlsx'));
      return;
    }
    cb(null, true);
  },
});

export const excelImportRouter = Router();

excelImportRouter.post(
  '/:id/importar-excel',
  requireRole(RolUsuario.ADMIN),
  upload.single('archivo'),
  asyncHandler(async (req, res) => {
    const { id } = importarExcelParamsSchema.parse(req.params);
    if (!req.file) {
      throw new ValidationError('Falta el archivo (campo "archivo" en el form-data)');
    }

    const resumen = await importarExcel(id, {
      buffer: req.file.buffer ?? fs.readFileSync(req.file.path),
      nombreOriginal: req.file.originalname,
      rutaGuardada: req.file.path,
    });

    res.status(201).json(resumen);
  }),
);
