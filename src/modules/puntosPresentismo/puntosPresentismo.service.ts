import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { validarCategoriaYZona } from '../common/validarCategoriaZona';
import { CrearPuntosPresentismoInput } from './puntosPresentismo.validation';

export async function crearPuntosPresentismo(input: CrearPuntosPresentismoInput) {
  const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
  if (!equipo) {
    throw new NotFoundError('Equipo', input.equipoId);
  }

  const zonaId = input.zonaId ?? undefined;
  await validarCategoriaYZona(input.categoriaId, zonaId);

  return prisma.puntosPresentismo.create({
    data: {
      equipoId: input.equipoId,
      categoriaId: input.categoriaId,
      zonaId,
      puntos: input.puntos,
      motivo: input.motivo,
    },
  });
}

export async function eliminarPuntosPresentismo(id: number): Promise<void> {
  const puntosPresentismo = await prisma.puntosPresentismo.findUnique({ where: { id } });
  if (!puntosPresentismo) {
    throw new NotFoundError('PuntosPresentismo', id);
  }
  // Igual que con los partidos: las tablas se calculan al vuelo, el presentismo
  // desaparece solo de la tabla apenas se borra su registro.
  await prisma.puntosPresentismo.delete({ where: { id } });
}