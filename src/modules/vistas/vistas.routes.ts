import { Router } from 'express';
import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { obtenerStandings } from '../standings/standings.service';
import { listarPartidos } from '../partidos/partidos.service';
import { listarGoleadores } from '../goleadores/goleadores.service';
import { listarFiguras } from '../figuras/figuras.service';
import { listarImbatibles } from '../imbatibles/imbatibles.service';
import { listarSancionados } from '../sanciones/sanciones.service';
import { obtenerHistorialCampeones } from '../campeones/campeones.service';
import { asyncViewHandler } from './vistaErrorHandler';

export const vistasRouter = Router();

vistasRouter.get(
  '/',
  asyncViewHandler(async (_req, res) => {
    const torneos = await prisma.torneo.findMany({
      include: { temporada: true, categorias: { include: { zonas: true } } },
      orderBy: [{ temporada: { anio: 'desc' } }, { nombre: 'asc' }],
    });
    res.render('home', { titulo: 'Inicio', torneos });
  }),
);

vistasRouter.get(
  '/standings',
  asyncViewHandler(async (req, res) => {
    const categoriaId = Number(req.query.categoriaId);
    const zonaId = req.query.zonaId ? Number(req.query.zonaId) : undefined;
    if (!categoriaId) {
      res.redirect('/panel');
      return;
    }

    const categoria = await prisma.categoria.findUnique({
      where: { id: categoriaId },
      include: { torneo: { include: { temporada: true } } },
    });
    if (!categoria) throw new NotFoundError('Categoria', categoriaId);
    const zona = zonaId ? await prisma.zona.findUnique({ where: { id: zonaId } }) : null;

    const tabla = await obtenerStandings(categoriaId, zonaId);
    res.render('standings', { titulo: 'Posiciones', tabla, categoria, zona, torneo: categoria.torneo });
  }),
);

vistasRouter.get(
  '/partidos',
  asyncViewHandler(async (req, res) => {
    const partidos = await listarPartidos({
      equipoId: req.query.equipoId ? Number(req.query.equipoId) : undefined,
      categoriaId: req.query.categoriaId ? Number(req.query.categoriaId) : undefined,
      jornada: req.query.jornada ? Number(req.query.jornada) : undefined,
    });
    res.render('partidos', { titulo: 'Partidos', partidos });
  }),
);

async function obtenerTorneoOrFallar(torneoId: number) {
  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId }, include: { temporada: true } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);
  return torneo;
}

vistasRouter.get(
  '/goleadores',
  asyncViewHandler(async (req, res) => {
    const torneoId = Number(req.query.torneoId);
    if (!torneoId) {
      res.redirect('/panel');
      return;
    }
    const [datos, torneo] = await Promise.all([listarGoleadores(torneoId), obtenerTorneoOrFallar(torneoId)]);
    const filas = datos.map((d) => ({ jugador: d.jugador, valor: d.goles }));
    res.render('ranking-jugador', { titulo: 'Goleadores', torneo, filas, columnaValor: 'Goles' });
  }),
);

vistasRouter.get(
  '/figuras',
  asyncViewHandler(async (req, res) => {
    const torneoId = Number(req.query.torneoId);
    if (!torneoId) {
      res.redirect('/panel');
      return;
    }
    const [datos, torneo] = await Promise.all([listarFiguras(torneoId), obtenerTorneoOrFallar(torneoId)]);
    const filas = datos.map((d) => ({ jugador: d.jugador, valor: d.veces }));
    res.render('ranking-jugador', { titulo: 'Figuras', torneo, filas, columnaValor: 'Veces figura' });
  }),
);

vistasRouter.get(
  '/imbatibles',
  asyncViewHandler(async (req, res) => {
    const torneoId = Number(req.query.torneoId);
    if (!torneoId) {
      res.redirect('/panel');
      return;
    }
    const [datos, torneo] = await Promise.all([listarImbatibles(torneoId), obtenerTorneoOrFallar(torneoId)]);
    const filas = datos.map((d) => ({ jugador: d.jugador, valor: d.golesRecibidos }));
    res.render('ranking-jugador', { titulo: 'Imbatibles', torneo, filas, columnaValor: 'Goles recibidos' });
  }),
);

vistasRouter.get(
  '/sancionados',
  asyncViewHandler(async (req, res) => {
    const torneoId = Number(req.query.torneoId);
    if (!torneoId) {
      res.redirect('/panel');
      return;
    }
    const [sancionados, torneo] = await Promise.all([listarSancionados(torneoId), obtenerTorneoOrFallar(torneoId)]);
    res.render('sancionados', { titulo: 'Sancionados', sancionados, torneo });
  }),
);

vistasRouter.get(
  '/campeones',
  asyncViewHandler(async (_req, res) => {
    const { historial, maximosCampeones } = await obtenerHistorialCampeones();
    res.render('campeones', { titulo: 'Campeones', historial, maximosCampeones });
  }),
);
