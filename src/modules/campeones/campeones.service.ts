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

  // @@unique([anio, categoriaId]) del schema protege el duplicado a nivel DB
  // (409 UNIQUE_CONSTRAINT vía errorHandler si ya hay un campeón cargado
  // para ese año + categoría).
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
 */
export async function obtenerMaximosCampeones(categoriaId?: number): Promise<MaximoCampeon[]> {
  const agrupado = await prisma.campeon.groupBy({
    by: ['equipoId'],
    where: categoriaId !== undefined ? { categoriaId } : undefined,
    _count: { equipoId: true },
  });

  if (agrupado.length === 0) return [];

  const equipos = await prisma.equipo.findMany({
    where: { id: { in: agrupado.map((a: { equipoId: number }) => a.equipoId) } },
  });
  const nombrePorId = new Map(equipos.map((e: { id: number; nombre: string }) => [e.id, e.nombre]));

  return agrupado
    .map((a: { equipoId: number; _count: { equipoId: number } }) => ({
      equipoId: a.equipoId,
      nombre: nombrePorId.get(a.equipoId) ?? '(equipo desconocido)',
      titulos: a._count.equipoId,
    }))
    .sort((a: MaximoCampeon, b: MaximoCampeon) => b.titulos - a.titulos || a.nombre.localeCompare(b.nombre));
}

export async function obtenerHistorialCampeones(categoriaId?: number) {
  const [historial, maximosCampeones] = await Promise.all([
    prisma.campeon.findMany({
      where: categoriaId !== undefined ? { categoriaId } : undefined,
      include: { categoria: { include: { torneo: true } }, equipo: true },
      orderBy: [{ anio: 'desc' }],
    }),
    obtenerMaximosCampeones(categoriaId),
  ]);

  return { historial, maximosCampeones };
}
