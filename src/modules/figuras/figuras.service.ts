import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';

export async function listarFiguras(torneoId: number) {
  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);

  return prisma.figura.findMany({
    where: { torneoId },
    include: { jugador: { include: { equipo: true } } },
    orderBy: { veces: 'desc' },
  });
}

// Ídem goleadores.service.ts: sin endpoint HTTP propio, lo usa el importador de Excel.
export async function establecerFigura(jugadorId: number, torneoId: number, veces: number) {
  return prisma.figura.upsert({
    where: { jugadorId_torneoId: { jugadorId, torneoId } },
    create: { jugadorId, torneoId, veces },
    update: { veces },
  });
}
