import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { obtenerStatsEquipo } from '../standings/standings.service';
import { ActualizarEquipoInput, CrearEquipoInput } from './equipos.validation';

export async function listarEquipos() {
  return prisma.equipo.findMany({ orderBy: { nombre: 'asc' } });
}

export async function obtenerEquipo(id: number) {
  const equipo = await prisma.equipo.findUnique({ where: { id } });
  if (!equipo) throw new NotFoundError('Equipo', id);
  return equipo;
}

export async function crearEquipo(input: CrearEquipoInput) {
  return prisma.equipo.create({ data: input });
}

export async function actualizarEquipo(id: number, input: ActualizarEquipoInput) {
  await obtenerEquipo(id);
  return prisma.equipo.update({ where: { id }, data: input });
}

export async function eliminarEquipo(id: number) {
  await obtenerEquipo(id);
  await prisma.equipo.delete({ where: { id } });
}

// GET /equipos/{id}/jugadores — plantel del equipo (spec: uso principal es
// para el delegado, pero está listado junto a los endpoints públicos).
export async function obtenerPlantel(id: number) {
  await obtenerEquipo(id);
  return prisma.jugador.findMany({ where: { equipoId: id }, orderBy: { nombre: 'asc' } });
}

// GET /equipos/{id}/stats — la spec no aclara para qué categoría/zona
// (un equipo puede jugar distintas categorías en distintos torneos), así
// que aceptamos categoriaId/zonaId opcionales por query y, si no vienen,
// usamos la inscripción más reciente del equipo (torneo/temporada más
// nuevo). Documentado acá porque no está en DECISIONES.md original.
export async function obtenerStatsEquipoActual(equipoId: number, categoriaId?: number, zonaId?: number) {
  await obtenerEquipo(equipoId);

  if (categoriaId !== undefined) {
    return obtenerStatsEquipo(equipoId, categoriaId, zonaId);
  }

  const inscripcionReciente = await prisma.inscripcionEquipo.findFirst({
    where: { equipoId },
    include: { categoria: { include: { torneo: { include: { temporada: true } } } } },
    orderBy: [
      { categoria: { torneo: { temporada: { anio: 'desc' } } } },
      { categoria: { torneo: { nombre: 'desc' } } },
    ],
  });

  if (!inscripcionReciente) {
    throw new NotFoundError('Inscripción de ese equipo en algún torneo');
  }

  return obtenerStatsEquipo(equipoId, inscripcionReciente.categoriaId, inscripcionReciente.zonaId ?? undefined);
}
