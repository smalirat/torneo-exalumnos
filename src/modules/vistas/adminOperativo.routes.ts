import { Response, Router } from 'express';
import { z } from 'zod';

import { prisma } from '../../lib/prisma';
import { NotFoundError, ValidationError } from '../../utils/AppError';

import { asyncViewHandler } from './vistaErrorHandler';

import { actualizarPartido, eliminarPartido } from '../partidos/partidos.service';
import { actualizarPartidoSchema } from '../partidos/partidos.validation';

import {
  actualizarJugador,
  crearJugador,
  eliminarJugador,
  listarJugadores,
} from '../jugadores/jugadores.service';
import { actualizarJugadorSchema, crearJugadorSchema } from '../jugadores/jugadores.validation';

import { eliminarAmonestacion, registrarAmonestacion } from '../amonestaciones/amonestaciones.service';
import { registrarAmonestacionSchema } from '../amonestaciones/amonestaciones.validation';

import { crearPuntosRestados, eliminarPuntosRestados } from '../puntosRestados/puntosRestados.service';
import { crearPuntosRestadosSchema } from '../puntosRestados/puntosRestados.validation';

import { actualizarEstadisticas } from '../estadisticas/estadisticas.service';

import { crearCampeon, eliminarCampeon, obtenerHistorialCampeones } from '../campeones/campeones.service';
import { crearCampeonSchema } from '../campeones/campeones.validation';

export const adminOperativoRouter = Router();

// ============================================================
// Helpers compartidos
// ============================================================

function mensajeErrorFormulario(err: unknown): string {
  if (err instanceof z.ZodError) {
    return err.issues.map((issue) => issue.message).join(' ');
  }
  if (err instanceof Error) return err.message;
  return 'No se pudo guardar el cambio.';
}

function redirectConMensaje(res: Response, urlBase: string, ok: string, err?: unknown) {
  if (err) {
    res.redirect(urlBase + '?error=' + encodeURIComponent(mensajeErrorFormulario(err)));
    return;
  }
  res.redirect(urlBase + '?ok=' + encodeURIComponent(ok));
}

const destinoRegex = /^(categoria|zona):(\d+)$/;

async function resolverDestinoAdministrativo(destino: string): Promise<{ categoriaId: number; zonaId?: number }> {
  const match = destino.match(destinoRegex);
  if (!match) throw new ValidationError('Destino inválido.');

  if (match[1] === 'categoria') {
    return { categoriaId: Number(match[2]) };
  }

  const zona = await prisma.zona.findUnique({ where: { id: Number(match[2]) } });
  if (!zona) throw new NotFoundError('Zona', Number(match[2]));
  return { categoriaId: zona.categoriaId, zonaId: zona.id };
}

async function construirDestinosAdmin() {
  const categorias = await prisma.categoria.findMany({
    include: {
      zonas: { orderBy: { nombre: 'asc' } },
      torneo: { include: { temporada: true } },
    },
    orderBy: [{ torneo: { temporada: { anio: 'desc' } } }, { torneo: { nombre: 'asc' } }, { nombre: 'asc' }],
  });

  return categorias.flatMap((categoria) => {
    const prefijo =
      `${categoria.torneo.temporada.anio} — ${categoria.torneo.nombre} — Categoría ${categoria.nombre}`;

    if (categoria.zonas.length === 0) {
      return [{ value: `categoria:${categoria.id}`, label: `${prefijo} — Sin zonas` }];
    }

    return categoria.zonas.map((zona) => ({
      value: `zona:${zona.id}`,
      label: `${prefijo} — ${zona.nombre}`,
    }));
  });
}

async function torneosParaSelect() {
  return prisma.torneo.findMany({
    include: { temporada: true },
    orderBy: [{ temporada: { anio: 'desc' } }, { nombre: 'asc' }],
  });
}

// ============================================================
// PARTIDOS (listado admin + baja)
// ============================================================

adminOperativoRouter.get('/partidos', asyncViewHandler(async (_req, res) => {
  const partidos = await prisma.partido.findMany({
    include: {
      equipoLocal: true,
      equipoVisitante: true,
      categoria: { include: { torneo: { include: { temporada: true } } } },
      zona: true,
    },
    orderBy: [{ fecha: 'desc' }, { jornada: 'desc' }],
  });
  res.render('admin/partidos', {
    titulo: 'Partidos (admin)',
    partidos,
    ok: _req.query.ok,
    error: _req.query.error,
  });
}));

adminOperativoRouter.post('/partidos/:id/eliminar', asyncViewHandler(async (req, res) => {
  try {
    await eliminarPartido(Number(req.params.id));
    redirectConMensaje(res, '/panel/admin/partidos', 'Partido eliminado.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/partidos', '', err);
  }
}));

// ============================================================
// JUGADORES (CRUD por equipo)
// ============================================================

adminOperativoRouter.get('/jugadores', asyncViewHandler(async (req, res) => {
  const equipoId = req.query.equipoId ? Number(req.query.equipoId) : undefined;
  const [equipos, jugadores] = await Promise.all([
    prisma.equipo.findMany({ orderBy: { nombre: 'asc' } }),
    listarJugadores(equipoId),
  ]);
  res.render('admin/jugadores', {
    titulo: 'Jugadores',
    equipos,
    jugadores,
    equipoId,
    ok: req.query.ok,
    error: req.query.error,
  });
}));

adminOperativoRouter.post('/jugadores', asyncViewHandler(async (req, res) => {
  try {
    await crearJugador(crearJugadorSchema.parse(req.body));
    redirectConMensaje(res, '/panel/admin/jugadores', 'Jugador creado.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/jugadores', '', err);
  }
}));

adminOperativoRouter.post('/jugadores/:id', asyncViewHandler(async (req, res) => {
  try {
    const body = { ...req.body };
    if (body.equipoId === '') body.equipoId = null;
    await actualizarJugador(Number(req.params.id), actualizarJugadorSchema.parse(body));
    redirectConMensaje(res, '/panel/admin/jugadores', 'Jugador actualizado.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/jugadores', '', err);
  }
}));

adminOperativoRouter.post('/jugadores/:id/eliminar', asyncViewHandler(async (req, res) => {
  try {
    await eliminarJugador(Number(req.params.id));
    redirectConMensaje(res, '/panel/admin/jugadores', 'Jugador eliminado.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/jugadores', '', err);
  }
}));

// ============================================================
// DISCIPLINA (amarillas + quita de puntos)
// ============================================================

adminOperativoRouter.get('/disciplina', asyncViewHandler(async (_req, res) => {
  const [torneos, equipos, jugadores, categorias, amonestaciones, puntosRestados] = await Promise.all([
    torneosParaSelect(),
    prisma.equipo.findMany({ orderBy: { nombre: 'asc' } }),
    prisma.jugador.findMany({ where: { equipoId: { not: null } }, include: { equipo: true }, orderBy: { nombre: 'asc' } }),
    construirDestinosAdmin(),
    prisma.amonestacion.findMany({
      include: { jugador: true, equipo: true, torneo: { include: { temporada: true } } },
      orderBy: [{ equipo: { nombre: 'asc' } }, { jugador: { nombre: 'asc' } }],
    }),
    prisma.puntosRestados.findMany({
      include: { equipo: true, categoria: { include: { torneo: { include: { temporada: true } } } }, zona: true },
      orderBy: { fecha: 'desc' },
    }),
  ]);

  res.render('admin/disciplina', {
    titulo: 'Disciplina',
    torneos,
    equipos,
    jugadores,
    destinos: categorias,
    amonestaciones,
    puntosRestados,
    ok: _req.query.ok,
    error: _req.query.error,
  });
}));

adminOperativoRouter.post('/disciplina/amonestaciones', asyncViewHandler(async (req, res) => {
  try {
    const input = registrarAmonestacionSchema.parse(req.body);
    await registrarAmonestacion(input);
    redirectConMensaje(res, '/panel/admin/disciplina', 'Amarilla registrada.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/disciplina', '', err);
  }
}));

adminOperativoRouter.post('/amonestaciones/:id/eliminar', asyncViewHandler(async (req, res) => {
  try {
    await eliminarAmonestacion(Number(req.params.id));
    redirectConMensaje(res, '/panel/admin/disciplina', 'Amarilla quitada.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/disciplina', '', err);
  }
}));

adminOperativoRouter.post('/disciplina/puntos-restados', asyncViewHandler(async (req, res) => {
  try {
    const destino = String(req.body.destino ?? '');
    const { categoriaId, zonaId } = await resolverDestinoAdministrativo(destino);

    const input = crearPuntosRestadosSchema.parse({
      equipoId: req.body.equipoId,
      categoriaId,
      zonaId,
      puntos: req.body.puntos,
      motivo: req.body.motivo,
    });
    await crearPuntosRestados(input);
    redirectConMensaje(res, '/panel/admin/disciplina', 'Quita de puntos cargada.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/disciplina', '', err);
  }
}));

adminOperativoRouter.post('/puntos-restados/:id/eliminar', asyncViewHandler(async (req, res) => {
  try {
    await eliminarPuntosRestados(Number(req.params.id));
    redirectConMensaje(res, '/panel/admin/disciplina', 'Quita de puntos eliminada.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/disciplina', '', err);
  }
}));

// ============================================================
// CAMPEONES (carga manual + historial)
// ============================================================

adminOperativoRouter.get('/campeones', asyncViewHandler(async (_req, res) => {
  const [categorias, equipos, historialCampeones] = await Promise.all([
    prisma.categoria.findMany({
      include: { torneo: { include: { temporada: true } } },
      orderBy: [{ torneo: { temporada: { anio: 'desc' } } }, { nombre: 'asc' }],
    }),
    prisma.equipo.findMany({ orderBy: { nombre: 'asc' } }),
    obtenerHistorialCampeones(),
  ]);

  res.render('admin/campeones', {
    titulo: 'Campeones (admin)',
    categorias,
    equipos,
    historial: historialCampeones.historial,
    maximosCampeones: historialCampeones.maximosCampeones,
    anioActual: new Date().getFullYear(),
    ok: _req.query.ok,
    error: _req.query.error,
  });
}));

adminOperativoRouter.post('/campeones', asyncViewHandler(async (req, res) => {
  try {
    await crearCampeon(crearCampeonSchema.parse(req.body));
    redirectConMensaje(res, '/panel/admin/campeones', 'Campeón guardado.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/campeones', '', err);
  }
}));

adminOperativoRouter.post('/campeones/:id/eliminar', asyncViewHandler(async (req, res) => {
  try {
    await eliminarCampeon(Number(req.params.id));
    redirectConMensaje(res, '/panel/admin/campeones', 'Campeón eliminado.');
  } catch (err) {
    redirectConMensaje(res, '/panel/admin/campeones', '', err);
  }
}));

// ============================================================
// EVENTOS DE PARTIDO (Tarea 3: unificar resultado + stats)
// ============================================================

adminOperativoRouter.get('/eventos', asyncViewHandler(async (req, res) => {
  const torneos = await torneosParaSelect();
  const torneoId = req.query.torneoId ? Number(req.query.torneoId) : undefined;
  const jornada = req.query.jornada ? Number(req.query.jornada) : undefined;

  let partidos: Awaited<ReturnType<typeof prisma.partido.findMany>> = [];
  if (torneoId && jornada) {
    partidos = await prisma.partido.findMany({
      where: { categoria: { torneoId }, jornada },
      include: {
        categoria: true,
        zona: true,
        equipoLocal: { include: { jugadores: true } },
        equipoVisitante: { include: { jugadores: true } },
      },
      orderBy: { fecha: 'asc' },
    });
  }

  res.render('admin/eventos', {
    titulo: 'Eventos de partido',
    torneos,
    torneoId,
    jornada,
    partidos,
    ok: req.query.ok,
    error: req.query.error,
  });
}));

adminOperativoRouter.post('/eventos/partidos/:id', asyncViewHandler(async (req, res) => {
  const partidoId = Number(req.params.id);

  try {
    const partido = await prisma.partido.findUnique({
      where: { id: partidoId },
      include: { categoria: true },
    });
    if (!partido) throw new NotFoundError('Partido', partidoId);
    const torneoId = partido.categoria.torneoId;

    const aNumeroOpcional = (valor: unknown): number | null => {
      if (valor === '' || valor === undefined || valor === null) return null;
      return Number(valor);
    };

    const golesLocal = aNumeroOpcional(req.body.golesLocal);
    const golesVisitante = aNumeroOpcional(req.body.golesVisitante);

    await actualizarPartido(partidoId, actualizarPartidoSchema.parse({ golesLocal, golesVisitante }));

    const resumen = { goles: 0, mvp: 0, amarillas: 0 };

    const sumarGol = async (jugadorIdRaw: unknown) => {
      if (jugadorIdRaw === '' || jugadorIdRaw === undefined) return;
      await actualizarEstadisticas({
        jugadorId: Number(jugadorIdRaw),
        torneoId,
        goles: 1,
      });
      resumen.goles++;
    };

    const registrarFigura = async (jugadorIdRaw: unknown) => {
      if (jugadorIdRaw === '' || jugadorIdRaw === undefined) return;
      await actualizarEstadisticas({
        jugadorId: Number(jugadorIdRaw),
        torneoId,
        mvp: 1,
      });
      resumen.mvp++;
    };

    const registrarAmarilla = async (jugadorIdRaw: unknown) => {
      if (jugadorIdRaw === '' || jugadorIdRaw === undefined) return;
      const jugador = await prisma.jugador.findUnique({ where: { id: Number(jugadorIdRaw) } });
      if (!jugador) throw new NotFoundError('Jugador', Number(jugadorIdRaw));
      if (jugador.equipoId === null) {
        throw new ValidationError(`El jugador "${jugador.nombre}" no tiene equipo asignado.`);
      }
      await registrarAmonestacion({
        jugadorId: jugador.id,
        equipoId: jugador.equipoId,
        torneoId,
        cantidad: 1,
      });
      resumen.amarillas++;
    };

    await Promise.all([
      sumarGol(req.body.goleadorLocalId),
      sumarGol(req.body.goleadorVisitanteId),
      registrarFigura(req.body.figuraId),
      registrarAmarilla(req.body.amarillaId),
    ]);

    const base = `/panel/admin/eventos?torneoId=${torneoId}&jornada=${partido.jornada}`;
    const detalle = Object.entries(resumen)
      .filter(([, cantidad]) => cantidad > 0)
      .map(([clave, cantidad]) => `${clave}: ${cantidad}`);
    redirectConMensaje(res, base, `Resultado guardado (${detalle.join(', ') || 'solo resultado'}).`);
  } catch (err) {
    const base = `/panel/admin/eventos?torneoId=${req.body.torneoId ?? ''}&jornada=${req.body.jornada ?? ''}`;
    redirectConMensaje(res, base, '', err);
  }
}));