import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarCampeonInput, CrearCampeonInput } from './campeones.validation';

export async function crearCampeon(input: CrearCampeonInput) {
  const [categoria, equipo] = await Promise.all([
    prisma.categoria.findUnique({ where: { id: input.categoriaId } }),
    prisma.equipo.findUnique({ where: { id: input.equipoId } }),
  ]);
  if (!categoria) throw new NotFoundError('Categoria', input.categoriaId);
  if (!equipo) throw new NotFoundError('Equipo', input.equipoId);

  // @@unique([anio, categoriaId, instancia]) del schema protege el duplicado
  // a nivel DB (409 UNIQUE_CONSTRAINT vía errorHandler si ya hay un campeón
  // cargado para ese año + categoría + instancia).
  return prisma.campeon.create({ data: input });
}

export async function actualizarCampeon(id: number, input: ActualizarCampeonInput) {
  const campeon = await prisma.campeon.findUnique({ where: { id } });
  if (!campeon) throw new NotFoundError('Campeon', id);

  if (input.equipoId !== undefined) {
    const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
    if (!equipo) throw new NotFoundError('Equipo', input.equipoId);
  }

  return prisma.campeon.update({ where: { id }, data: input });
}

export async function eliminarCampeon(id: number) {
  const campeon = await prisma.campeon.findUnique({ where: { id } });
  if (!campeon) throw new NotFoundError('Campeon', id);
  await prisma.campeon.delete({ where: { id } });
}

export interface MaximoCampeon {
  equipoId: number;
  nombre: string;
  titulos: number;
}

/**
 * Ranking de "máximos campeones": se calcula agrupando la tabla Campeon,
 * como pediste — no existe una entidad/tabla aparte para esto.
 *
 * Un año cuenta UNA vez por equipo aunque tenga varias filas (ej. ganó
 * Apertura y la Final: 2 filas en persistencia, pero para el usuario fue
 * campeón una sola vez ese año). Por eso se agrupa por año+categoría
 * distintos, no por cantidad de filas.
 */
export async function obtenerMaximosCampeones(categoriaId?: number): Promise<MaximoCampeon[]> {
  const filas = await prisma.campeon.findMany({
    where: categoriaId !== undefined ? { categoriaId } : undefined,
    select: { anio: true, categoriaId: true, equipoId: true },
  });

  if (filas.length === 0) return [];

  const aniosPorEquipo = new Map<number, Set<string>>();
  for (const f of filas) {
    const clave = `${f.anio}|${f.categoriaId}`;
    let set = aniosPorEquipo.get(f.equipoId);
    if (!set) {
      set = new Set();
      aniosPorEquipo.set(f.equipoId, set);
    }
    set.add(clave);
  }

  const equipos = await prisma.equipo.findMany({
    where: { id: { in: [...aniosPorEquipo.keys()] } },
  });
  const nombrePorId = new Map(equipos.map((e: { id: number; nombre: string }) => [e.id, e.nombre]));

  return [...aniosPorEquipo.entries()]
    .map(([equipoId, anios]) => ({
      equipoId,
      nombre: nombrePorId.get(equipoId) ?? '(equipo desconocido)',
      titulos: anios.size,
    }))
    .sort((a: MaximoCampeon, b: MaximoCampeon) => b.titulos - a.titulos || a.nombre.localeCompare(b.nombre));
}

export async function obtenerHistorialCampeones(categoriaId?: number) {
  const [historial, maximosCampeones] = await Promise.all([
    prisma.campeon.findMany({
      where: categoriaId !== undefined ? { categoriaId } : undefined,
      include: { categoria: { include: { torneo: true } }, equipo: true },
      orderBy: [{ anio: 'desc' }, { categoriaId: 'asc' }, { instancia: 'asc' }],
    }),
    obtenerMaximosCampeones(categoriaId),
  ]);

  return { historial, maximosCampeones };
}
