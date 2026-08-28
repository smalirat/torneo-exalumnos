import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';
import { ActualizarTorneoInput, CrearTorneoInput } from './torneos.validation';

export async function listarTorneos(temporadaId?: number) {
  return prisma.torneo.findMany({
    where: { temporadaId },
    include: { temporada: true },
    orderBy: [{ temporadaId: 'desc' }, { nombre: 'asc' }],
  });
}

export async function obtenerTorneo(id: number) {
  const torneo = await prisma.torneo.findUnique({ where: { id }, include: { temporada: true } });
  if (!torneo) throw new NotFoundError('Torneo', id);
  return torneo;
}

export async function crearTorneo(input: CrearTorneoInput) {
  const temporada = await prisma.temporada.findUnique({ where: { id: input.temporadaId } });
  if (!temporada) throw new NotFoundError('Temporada', input.temporadaId);

  return prisma.torneo.create({ data: input });
}

export async function actualizarTorneo(id: number, input: ActualizarTorneoInput) {
  await obtenerTorneo(id);
  return prisma.torneo.update({ where: { id }, data: input });
}

export async function eliminarTorneo(id: number) {
  await obtenerTorneo(id);
  await prisma.torneo.delete({ where: { id } });
}
