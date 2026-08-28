import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';

export async function listarGoleadores(torneoId: number) {
  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);

  return prisma.goleador.findMany({
    where: { torneoId },
    include: { jugador: { include: { equipo: true } } },
    orderBy: { goles: 'desc' },
  });
}

/**
 * No hay endpoint HTTP para esto todavía (la spec no lista un POST/PUT para
 * Goleador — solo se llena vía Excel). Lo dejamos como función de service
 * lista para que el importador de Excel (próxima parte) la use directamente,
 * sin tener que duplicar la lógica de upsert.
 */
export async function establecerGoleador(jugadorId: number, torneoId: number, goles: number) {
  return prisma.goleador.upsert({
    where: { jugadorId_torneoId: { jugadorId, torneoId } },
    create: { jugadorId, torneoId, goles },
    update: { goles },
  });
}
