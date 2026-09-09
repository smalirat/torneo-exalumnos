import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { validarCategoriaYZona } from '../common/validarCategoriaZona';
import { CrearPuntosRestadosInput } from './puntosRestados.validation';

export async function crearPuntosRestados(input: CrearPuntosRestadosInput) {
  const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
  if (!equipo) {
    throw new NotFoundError('Equipo', input.equipoId);
  }

  const zonaId = input.zonaId ?? undefined;
  await validarCategoriaYZona(input.categoriaId, zonaId);

  return prisma.puntosRestados.create({
    data: {
      equipoId: input.equipoId,
      categoriaId: input.categoriaId,
      zonaId,
      puntos: input.puntos,
      motivo: input.motivo,
    },
  });
}

export async function eliminarPuntosRestados(id: number): Promise<void> {
  const puntosRestados = await prisma.puntosRestados.findUnique({ where: { id } });
  if (!puntosRestados) {
    throw new NotFoundError('PuntosRestados', id);
  }
  // Igual que con los partidos: las tablas se calculan al vuelo, el descuento
  // desaparece solo de la tabla apenas se borra su registro.
  await prisma.puntosRestados.delete({ where: { id } });
}