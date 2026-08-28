import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarCategoriaInput, CrearCategoriaInput } from './categorias.validation';

export async function listarCategorias(torneoId?: number) {
  return prisma.categoria.findMany({
    where: { torneoId },
    include: { zonas: true },
    orderBy: { nombre: 'asc' },
  });
}

export async function obtenerCategoria(id: number) {
  const categoria = await prisma.categoria.findUnique({ where: { id }, include: { zonas: true } });
  if (!categoria) throw new NotFoundError('Categoria', id);
  return categoria;
}

export async function crearCategoria(input: CrearCategoriaInput) {
  const torneo = await prisma.torneo.findUnique({ where: { id: input.torneoId } });
  if (!torneo) throw new NotFoundError('Torneo', input.torneoId);

  return prisma.categoria.create({ data: input });
}

export async function actualizarCategoria(id: number, input: ActualizarCategoriaInput) {
  await obtenerCategoria(id);
  return prisma.categoria.update({ where: { id }, data: input });
}

export async function eliminarCategoria(id: number) {
  await obtenerCategoria(id);
  await prisma.categoria.delete({ where: { id } });
}
