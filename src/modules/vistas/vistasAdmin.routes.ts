import { Router } from 'express';
import { RolUsuario } from '@prisma/client';
import { prisma } from '../../lib/prisma';
import { requireRoleView } from '../../middleware/authView';
import { asyncViewHandler, manejarErrorVista } from './vistaErrorHandler';
import { crearPartidoSchema } from '../partidos/partidos.validation';
import { crearPartido } from '../partidos/partidos.service';
import { crearSancionSchema } from '../sanciones/sanciones.validation';
import { crearSancion } from '../sanciones/sanciones.service';
import { importarExcel } from '../excel-import/excelImport.service';
import { upload } from '../excel-import/excelImport.routes';

export const vistasAdminRouter = Router();

// Todo lo que sigue requiere estar logueado como ADMIN.
vistasAdminRouter.use(requireRoleView(RolUsuario.ADMIN));

vistasAdminRouter.get('/', (req, res) => {
  res.render('admin/dashboard', { titulo: 'Admin', ok: req.query.ok, error: req.query.error });
});

// ---------- Cargar resultado de partido ----------
async function categoriasParaSelect() {
  return prisma.categoria.findMany({
    include: { torneo: { include: { temporada: true } } },
    orderBy: [{ torneo: { temporada: { anio: 'desc' } } }],
  });
}

vistasAdminRouter.get(
  '/partidos/nuevo',
  asyncViewHandler(async (req, res) => {
    const categorias = await categoriasParaSelect();
    res.render('admin/partido-nuevo', { titulo: 'Cargar partido', categorias, ok: req.query.ok, error: req.query.error });
  }),
);

vistasAdminRouter.post(
  '/partidos',
  asyncViewHandler(async (req, res) => {
    try {
      // req.body llega todo como strings (form HTML clásico); crearPartidoSchema
      // usa z.coerce en los campos numéricos/fecha así que lo maneja bien.
      // zonaId/goles vacíos ("") los tratamos como "no vino" antes de parsear.
      const body = { ...req.body };
      for (const campo of ['zonaId', 'golesLocal', 'golesVisitante', 'campo', 'cancha', 'horario']) {
        if (body[campo] === '') delete body[campo];
      }
      const input = crearPartidoSchema.parse(body);
      await crearPartido(input);
      res.redirect('/panel/admin/partidos/nuevo?ok=' + encodeURIComponent('Partido guardado.'));
    } catch (err) {
      const categorias = await categoriasParaSelect();
      if (err instanceof Error) {
        res.status(400).render('admin/partido-nuevo', { titulo: 'Cargar partido', categorias, error: err.message });
        return;
      }
      manejarErrorVista(err, res);
    }
  }),
);

// ---------- Importar Excel ----------
async function torneosParaSelect() {
  return prisma.torneo.findMany({
    include: { temporada: true },
    orderBy: [{ temporada: { anio: 'desc' } }, { nombre: 'asc' }],
  });
}

vistasAdminRouter.get(
  '/importar-excel',
  asyncViewHandler(async (_req, res) => {
    const torneos = await torneosParaSelect();
    res.render('admin/importar-excel', { titulo: 'Importar Excel', torneos });
  }),
);

vistasAdminRouter.post(
  '/importar-excel',
  upload.single('archivo'),
  asyncViewHandler(async (req, res) => {
    const torneos = await torneosParaSelect();
    const torneoId = Number(req.body.torneoId);

    if (!req.file) {
      res.status(400).render('admin/importar-excel', { titulo: 'Importar Excel', torneos, error: 'Falta el archivo.' });
      return;
    }

    try {
      const fs = await import('fs');
      const resumen = await importarExcel(torneoId, {
        buffer: req.file.buffer ?? fs.readFileSync(req.file.path),
        nombreOriginal: req.file.originalname,
        rutaGuardada: req.file.path,
      });
      res.render('admin/importar-excel', { titulo: 'Importar Excel', torneos, resumen });
    } catch (err) {
      if (err instanceof Error) {
        res.status(400).render('admin/importar-excel', { titulo: 'Importar Excel', torneos, error: err.message });
        return;
      }
      manejarErrorVista(err, res);
    }
  }),
);

// ---------- Cargar sanción ----------
vistasAdminRouter.get(
  '/sanciones/nueva',
  asyncViewHandler(async (_req, res) => {
    const torneos = await torneosParaSelect();
    res.render('admin/sancion-nueva', { titulo: 'Cargar sanción', torneos });
  }),
);

vistasAdminRouter.post(
  '/sanciones',
  asyncViewHandler(async (req, res) => {
    try {
      const input = crearSancionSchema.parse(req.body);
      await crearSancion(input);
      res.redirect('/panel/admin?ok=' + encodeURIComponent('Sanción cargada.'));
    } catch (err) {
      const torneos = await torneosParaSelect();
      if (err instanceof Error) {
        res.status(400).render('admin/sancion-nueva', { titulo: 'Cargar sanción', torneos, error: err.message });
        return;
      }
      manejarErrorVista(err, res);
    }
  }),
);
