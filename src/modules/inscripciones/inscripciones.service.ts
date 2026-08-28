import { prisma } from '../../lib/prisma';
import { ConflictError, NotFoundError } from '../../utils/AppError';
import { validarCategoriaYZona } from '../common/validarCategoriaZona';
import { CrearInscripcionInput } from './inscripciones.validation';

export async function listarInscripciones(filtros: {
  equipoId?: number;
  categoriaId?: number;
  zonaId?: number;
}) {
  return prisma.inscripcionEquipo.findMany({
    where: filtros,
    include: { equipo: true, categoria: { include: { torneo: true } }, zona: true },
  });
}

export async function crearInscripcion(input: CrearInscripcionInput) {
  const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
  if (!equipo) throw new NotFoundError('Equipo', input.equipoId);

  await validarCategoriaYZona(input.categoriaId, input.zonaId);

  const categoria = await prisma.categoria.findUnique({ where: { id: input.categoriaId } });
  // validarCategoriaYZona ya garantiza que existe, esto es solo para leer torneoId
  const torneoId = categoria!.torneoId;

  // Regla de negocio (no viene de un @@unique porque cruza tablas): un
  // equipo no puede estar inscripto en más de una categoría dentro del
  // MISMO torneo. El @@unique([equipoId, categoriaId, zonaId]) del schema
  // solo evita el duplicado exacto; esto evita el caso "Equipo X anotado
  // en Categoría A y Categoría B del mismo Apertura 2026".
  const inscripcionExistenteEnTorneo = await prisma.inscripcionEquipo.findFirst({
    where: {
      equipoId: input.equipoId,
      categoria: { torneoId },
    },
    include: { categoria: true, zona: true },
  });

  if (inscripcionExistenteEnTorneo) {
    throw new ConflictError(
      `El equipo ya está inscripto en este torneo (categoría ${inscripcionExistenteEnTorneo.categoria.nombre}` +
        `${inscripcionExistenteEnTorneo.zona ? `, ${inscripcionExistenteEnTorneo.zona.nombre}` : ''}). ` +
        `Un equipo no puede jugar dos categorías en el mismo torneo.`,
    );
  }

  return prisma.inscripcionEquipo.create({ data: input });
}

export async function eliminarInscripcion(id: number) {
  const inscripcion = await prisma.inscripcionEquipo.findUnique({ where: { id } });
  if (!inscripcion) throw new NotFoundError('InscripcionEquipo', id);
  await prisma.inscripcionEquipo.delete({ where: { id } });
}
