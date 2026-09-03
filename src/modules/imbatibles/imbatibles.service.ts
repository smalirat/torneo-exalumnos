import { prisma } from '../../lib/prisma';
import { NotFoundError } from '../../utils/AppError';

// "Imbatible" es un ranking de ARQUEROS MENOS goleados: orden ascendente
// por golesRecibidos (al revés que goleadores/figuras, que son descendente).
export async function listarImbatibles(torneoId: number) {
  const torneo = await prisma.torneo.findUnique({ where: { id: torneoId } });
  if (!torneo) throw new NotFoundError('Torneo', torneoId);

  return prisma.imbatible.findMany({
    where: { torneoId },
    include: { jugador: { include: { equipo: true } } },
    orderBy: { golesRecibidos: 'asc' },
  });
}

// Ídem goleadores.service.ts: sin endpoint HTTP propio, lo usa el importador de Excel.
export async function establecerImbatible(jugadorId: number, torneoId: number, golesRecibidos: number) {
  return prisma.imbatible.upsert({
    where: { jugadorId_torneoId: { jugadorId, torneoId } },
    create: { jugadorId, torneoId, golesRecibidos },
    update: { golesRecibidos },
  });
}
