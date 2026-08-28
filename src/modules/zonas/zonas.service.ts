import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarZonaInput, CrearZonaInput } from './zonas.validation';

export async function listarZonas(categoriaId?: number) {
  return prisma.zona.findMany({ where: { categoriaId }, orderBy: { nombre: 'asc' } });
}

export async function obtenerZona(id: number) {
  const zona = await prisma.zona.findUnique({ where: { id } });
  if (!zona) throw new NotFoundError('Zona', id);
  return zona;
}

export async function crearZona(input: CrearZonaInput) {
  const categoria = await prisma.categoria.findUnique({ where: { id: input.categoriaId } });
  if (!categoria) throw new NotFoundError('Categoria', input.categoriaId);

  return prisma.zona.create({ data: input });
}

export async function actualizarZona(id: number, input: ActualizarZonaInput) {
  await obtenerZona(id);
  return prisma.zona.update({ where: { id }, data: input });
}

export async function eliminarZona(id: number) {
  await obtenerZona(id);
  await prisma.zona.delete({ where: { id } });
}
