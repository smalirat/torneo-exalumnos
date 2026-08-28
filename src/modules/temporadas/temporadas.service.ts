import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarTemporadaInput, CrearTemporadaInput } from './temporadas.validation';

export async function listarTemporadas() {
  return prisma.temporada.findMany({ orderBy: { anio: 'desc' } });
}

export async function obtenerTemporada(id: number) {
  const temporada = await prisma.temporada.findUnique({ where: { id } });
  if (!temporada) throw new NotFoundError('Temporada', id);
  return temporada;
}

export async function crearTemporada(input: CrearTemporadaInput) {
  // El @@unique([anio]) del schema ya protege esto a nivel DB; si choca,
  // el errorHandler lo traduce a 409 UNIQUE_CONSTRAINT automáticamente.
  return prisma.temporada.create({ data: input });
}

export async function actualizarTemporada(id: number, input: ActualizarTemporadaInput) {
  await obtenerTemporada(id); // 404 claro si no existe, antes de intentar el update
  return prisma.temporada.update({ where: { id }, data: input });
}

export async function eliminarTemporada(id: number) {
  await obtenerTemporada(id);
  // Si tiene Torneos asociados, Prisma tira P2003 (FK) -> el errorHandler
  // lo devuelve como 400 FOREIGN_KEY. Es un comportamiento razonable:
  // no queremos borrar en cascada una temporada con datos reales cargados.
  await prisma.temporada.delete({ where: { id } });
}
